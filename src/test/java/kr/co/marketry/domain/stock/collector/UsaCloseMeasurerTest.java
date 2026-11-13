package kr.co.marketry.domain.stock.collector;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.io.ByteArrayInputStream;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.concurrent.atomic.AtomicInteger;
import kr.co.marketry.domain.stock.client.YahooFinanceQuoteClient;
import kr.co.marketry.domain.stock.client.YahooFinanceQuoteClient.IndexSession;
import kr.co.marketry.domain.stock.client.YahooFinanceQuoteClient.Quote;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.web.client.HttpClientErrorException;

class UsaCloseMeasurerTest {
    private static final LocalDate DAY = LocalDate.of(2026, 11, 12);
    // 2026-11-12 16:00 EST = 21:00Z
    private static final Instant CLOSE = Instant.parse("2026-11-12T21:00:00Z");
    private static final String[] SYMBOLS = {"A", "B", "C", "D", "E", "F", "G", "H", "I", "J"};

    private YahooFinanceQuoteClient client;
    private MutableClock clock;

    @BeforeEach
    void setUp() {
        client = mock(YahooFinanceQuoteClient.class);
        when(client.fetchIndexSession("^GSPC")).thenReturn(new IndexSession(DAY, CLOSE));
    }

    @Test
    void 마감_전에는_종목을_조회하지_않고_건너뛴다() {
        UsaCloseMeasurer measurer = measurer(CLOSE.minusSeconds(60));

        UsaCloseMeasurer.Measurement result = measurer.measure();

        assertThat(result.wasSkipped()).isTrue();
        assertThat(result.reason()).isEqualTo("마감 전입니다");
        verify(client, never()).fetchQuote(anyString());
    }

    @Test
    void 정규_수집_시작_시각이_이미_지났으면_측정하지_않는다() {
        UsaCloseMeasurer measurer = measurer(CLOSE.plus(Duration.ofMinutes(30)));

        UsaCloseMeasurer.Measurement result = measurer.measure();

        assertThat(result.wasSkipped()).isTrue();
        assertThat(result.reason()).isEqualTo("측정 구간이 지났습니다");
        verify(client, never()).fetchQuote(anyString());
    }

    @Test
    void 야후가_이미_다음_거래일_종료_시각을_알려_줘도_그날_16시부터_측정한다() {
        // 마감 직후 최신 일봉은 11/12인데 종료 시각은 이미 11/13 16:00 ET로 넘어가 있다.
        when(client.fetchIndexSession("^GSPC"))
                .thenReturn(new IndexSession(DAY, Instant.parse("2026-11-13T21:00:00Z")));
        when(client.fetchQuote(anyString())).thenReturn(quote(CLOSE));
        UsaCloseMeasurer measurer = measurer(CLOSE.plusSeconds(60));

        UsaCloseMeasurer.Measurement result = measurer.measure();

        assertThat(result.wasSkipped()).isFalse();
        assertThat(result.closeAt()).isEqualTo(CLOSE);
    }

    @Test
    void 장_종료_시각이_최신_봉_날짜보다_앞서면_측정하지_않는다() {
        when(client.fetchIndexSession("^GSPC"))
                .thenReturn(new IndexSession(DAY, Instant.parse("2026-11-11T21:00:00Z")));
        UsaCloseMeasurer measurer = measurer(CLOSE.plusSeconds(60));

        UsaCloseMeasurer.Measurement result = measurer.measure();

        assertThat(result.wasSkipped()).isTrue();
        verify(client, never()).fetchQuote(anyString());
    }

    @Test
    void 처음에는_옛_시각이다가_나중에_종가_시각이_오면_90퍼센트_도달_시각을_기록하고_끝낸다() {
        UsaCloseMeasurer measurer = measurer(CLOSE.plusSeconds(60));
        AtomicInteger calls = new AtomicInteger();
        when(client.fetchQuote(anyString())).thenAnswer(invocation -> {
            // 첫 바퀴(10번 호출)는 종가 시각 전, 이후는 종가 시각
            Instant marketTime = calls.incrementAndGet() <= 10 ? CLOSE.minusSeconds(900) : CLOSE;
            return quote(marketTime);
        });

        UsaCloseMeasurer.Measurement result = measurer.measure();

        assertThat(result.reason()).isNull();
        assertThat(result.confirmedCount()).isGreaterThanOrEqualTo(9);
        assertThat(result.firstConfirmedAt()).isNotNull();
        assertThat(result.thresholdReachedAt()).isAfter(result.startedAt());
        assertThat(result.requestCount()).isEqualTo(10 + result.confirmedCount());
    }

    @Test
    void 정규_수집_시작_전까지_90퍼센트에_닿지_못하면_그_사유로_끝낸다() {
        UsaCloseMeasurer measurer = measurer(CLOSE.plusSeconds(60));
        when(client.fetchQuote(anyString())).thenReturn(quote(CLOSE.minusSeconds(900)));

        UsaCloseMeasurer.Measurement result = measurer.measure();

        assertThat(result.reason()).isEqualTo("정규 수집 시작 전 90% 미도달");
        assertThat(result.thresholdReachedAt()).isNull();
        assertThat(result.finishedAt()).isAfterOrEqualTo(CLOSE.plus(Duration.ofMinutes(30)));
    }

    @Test
    void 야후가_호출을_제한하면_바로_끝낸다() {
        UsaCloseMeasurer measurer = measurer(CLOSE.plusSeconds(60));
        when(client.fetchQuote(anyString())).thenThrow(new HttpClientErrorException(HttpStatus.TOO_MANY_REQUESTS));

        UsaCloseMeasurer.Measurement result = measurer.measure();

        assertThat(result.reason()).isEqualTo("야후 호출 제한");
        assertThat(result.requestCount()).isEqualTo(1);
    }

    @Test
    void 같은_날에는_두_번_시작하지_않는다() {
        UsaCloseMeasurer measurer = measurer(CLOSE.plusSeconds(60));
        when(client.fetchQuote(anyString())).thenReturn(quote(CLOSE));

        measurer.measure();
        UsaCloseMeasurer.Measurement second = measurer.measure();

        assertThat(second.wasSkipped()).isTrue();
        assertThat(second.reason()).isEqualTo("이미 측정했습니다");
    }

    private UsaCloseMeasurer measurer(Instant now) {
        clock = new MutableClock(now);
        StringBuilder csv = new StringBuilder("symbol,name,date_added\n");
        for (String symbol : SYMBOLS) {
            csv.append(symbol).append(",").append(symbol).append(" Inc,2020-01-01\n");
        }
        UsaConstituents constituents =
                new UsaConstituents(new ByteArrayInputStream(csv.toString().getBytes(StandardCharsets.UTF_8)));
        return new UsaCloseMeasurer(client, constituents, Duration.ofMinutes(30), clock::advance, clock);
    }

    private static Quote quote(Instant marketTime) {
        return new Quote(new BigDecimal("100"), new BigDecimal("99"), marketTime);
    }

    /** sleeper가 시간을 앞으로 옮기는 시계. 실제로 기다리지 않고 15초 간격 재조회를 시험한다. */
    private static final class MutableClock extends Clock {
        private Instant now;

        MutableClock(Instant now) {
            this.now = now;
        }

        void advance(Duration duration) {
            now = now.plus(duration);
        }

        @Override
        public ZoneId getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(ZoneId zone) {
            return this;
        }

        @Override
        public Instant instant() {
            return now;
        }
    }
}
