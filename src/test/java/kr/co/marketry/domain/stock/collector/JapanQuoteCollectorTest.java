package kr.co.marketry.domain.stock.collector;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import kr.co.marketry.domain.stock.client.YahooFinanceQuoteClient;
import kr.co.marketry.domain.stock.entity.OverseasStockQuote;
import kr.co.marketry.domain.stock.entity.OverseasStockQuoteId;
import kr.co.marketry.domain.stock.repository.OverseasStockQuoteRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.http.HttpStatus;
import org.springframework.web.client.HttpClientErrorException;

class JapanQuoteCollectorTest {
    private static final LocalDateTime LAST_TIME = LocalDateTime.of(2026, 11, 6, 15, 30);
    private static final LocalDate COLLECTION_DATE = LocalDate.of(2026, 11, 9);
    // 2026-11-09 10:07:30 KST
    private static final Instant MARKET_TIME = Instant.parse("2026-11-09T01:07:30Z");

    private YahooFinanceQuoteClient client;
    private OverseasStockQuoteRepository repository;
    private JapanQuoteCollector collector;

    @BeforeEach
    void setUp() {
        client = mock(YahooFinanceQuoteClient.class);
        repository = mock(OverseasStockQuoteRepository.class);
        collector = new JapanQuoteCollector(client, repository, duration -> {});
    }

    @Test
    void 받은_시세를_체결_시각의_5분_단위로_저장하고_시가총액을_가격만큼_조정한다() {
        givenLastQuotes(lastQuote("7203", "1000.00", "1000000"));
        when(client.fetchTokyoQuote("7203")).thenReturn(quote("1100", "1000", MARKET_TIME));

        JapanQuoteCollector.Result result = collector.collect(COLLECTION_DATE);

        assertThat(result.saved()).isTrue();
        assertThat(result.snapshotTime()).isEqualTo(LocalDateTime.of(2026, 11, 9, 10, 5));
        OverseasStockQuote saved = savedQuotes().getFirst();
        assertThat(saved.getId().getSnapshotTime()).isEqualTo(LocalDateTime.of(2026, 11, 9, 10, 5));
        assertThat(saved.getCurrentPrice()).isEqualByComparingTo("1100");
        assertThat(saved.getLastPrice()).isEqualByComparingTo("1000");
        assertThat(saved.getChangeRate()).isEqualByComparingTo("10.00");
        assertThat(saved.getTotalMarketValue()).isEqualByComparingTo("1100000");
    }

    @Test
    void 체결_시각이_마지막_시세보다_늦지_않으면_저장하지_않는다() {
        givenLastQuotes(lastQuote("7203", "1000.00", "1000000"));
        // 2026-11-06 15:30:02 KST — 휴장일에는 직전 거래일 시각이 그대로 온다.
        when(client.fetchTokyoQuote("7203")).thenReturn(quote("1000", "990", Instant.parse("2026-11-06T06:30:02Z")));

        JapanQuoteCollector.Result result = collector.collect(COLLECTION_DATE);

        assertThat(result.saved()).isFalse();
        assertThat(result.todayQuoteCount()).isZero();
        assertThat(result.otherDateQuoteCount()).isEqualTo(1);
        verify(repository, never()).saveAll(any());
    }

    @Test
    void 일부_종목을_받지_못하면_그_종목은_마지막_값을_유지한다() {
        List<OverseasStockQuote> last = new ArrayList<>();
        for (int index = 0; index < 10; index++) {
            last.add(lastQuote("C" + index, "1000.00", "1000000"));
        }
        givenLastQuotes(last.toArray(OverseasStockQuote[]::new));
        when(client.fetchTokyoQuote(anyString())).thenReturn(quote("1100", "1000", MARKET_TIME));
        when(client.fetchTokyoQuote("C3")).thenThrow(new IllegalStateException("없음"));

        JapanQuoteCollector.Result result = collector.collect(COLLECTION_DATE);

        assertThat(result.saved()).isTrue();
        assertThat(result.successCount()).isEqualTo(9);
        assertThat(result.todayQuoteCount()).isEqualTo(9);
        assertThat(result.otherDateQuoteCount()).isZero();
        assertThat(result.failureCount()).isEqualTo(1);
        OverseasStockQuote kept = savedQuotes().stream()
                .filter(quote -> quote.getId().getStockCode().equals("C3"))
                .findFirst()
                .orElseThrow();
        assertThat(kept.getCurrentPrice()).isEqualByComparingTo("1000.00");
        assertThat(kept.getTotalMarketValue()).isEqualByComparingTo("1000000");
        assertThat(kept.getId().getSnapshotTime()).isEqualTo(LocalDateTime.of(2026, 11, 9, 10, 5));
    }

    @Test
    void 받은_종목이_90퍼센트보다_적으면_저장하지_않는다() {
        givenLastQuotes(lastQuote("A", "1000.00", "1"), lastQuote("B", "1000.00", "1"));
        when(client.fetchTokyoQuote("A")).thenReturn(quote("1100", "1000", MARKET_TIME));
        when(client.fetchTokyoQuote("B")).thenThrow(new IllegalStateException("없음"));

        JapanQuoteCollector.Result result = collector.collect(COLLECTION_DATE);

        assertThat(result.saved()).isFalse();
        verify(repository, never()).saveAll(any());
    }

    @Test
    void 호출_제한을_받으면_남은_종목을_요청하지_않고_저장하지_않는다() {
        givenLastQuotes(lastQuote("A", "1000.00", "1"), lastQuote("B", "1000.00", "1"), lastQuote("C", "1000.00", "1"));
        when(client.fetchTokyoQuote("A"))
                .thenThrow(HttpClientErrorException.create(HttpStatus.TOO_MANY_REQUESTS, "", null, null, null));

        JapanQuoteCollector.Result result = collector.collect(COLLECTION_DATE);

        assertThat(result.saved()).isFalse();
        verify(client, times(1)).fetchTokyoQuote(anyString());
        verify(repository, never()).saveAll(any());
    }

    @Test
    void 기준_시세가_없으면_요청하지_않는다() {
        when(repository.findFirstByIdMarketOrderByIdSnapshotTimeDesc("JAPAN")).thenReturn(Optional.empty());

        JapanQuoteCollector.Result result = collector.collect(COLLECTION_DATE);

        assertThat(result.saved()).isFalse();
        verify(client, never()).fetchTokyoQuote(anyString());
    }

    @Test
    void 등락률은_전일_종가_기준_소수_둘째_자리로_반올림한다() {
        assertThat(JapanQuoteCollector.changeRateOf(new BigDecimal("1022.00"), new BigDecimal("1131.50")))
                .isEqualByComparingTo("-9.68");
    }

    @Test
    void 전일_시각_응답은_오늘_시세_집계에서_분리한다() {
        givenLastQuotes(lastQuote("7203", "1000.00", "1000000"), lastQuote("2914", "6269.00", "1000000"));
        when(client.fetchTokyoQuote("7203")).thenReturn(quote("1100", "1000", MARKET_TIME));
        when(client.fetchTokyoQuote("2914")).thenReturn(quote("6269", "6269", Instant.parse("2026-11-06T06:30:00Z")));

        JapanQuoteCollector.Result result = collector.collect(COLLECTION_DATE);

        assertThat(result.saved()).isTrue();
        assertThat(result.todayQuoteCount()).isEqualTo(1);
        assertThat(result.otherDateQuoteCount()).isEqualTo(1);
        assertThat(result.failureCount()).isZero();
        OverseasStockQuote savedHaltedStock = savedQuotes().stream()
                .filter(quote -> quote.getId().getStockCode().equals("2914"))
                .findFirst()
                .orElseThrow();
        assertThat(savedHaltedStock.getId().getSnapshotTime()).isEqualTo(result.snapshotTime());
    }

    private void givenLastQuotes(OverseasStockQuote... quotes) {
        when(repository.findFirstByIdMarketOrderByIdSnapshotTimeDesc("JAPAN")).thenReturn(Optional.of(quotes[0]));
        when(repository.findAllByIdMarketAndIdSnapshotTime("JAPAN", LAST_TIME)).thenReturn(List.of(quotes));
    }

    @SuppressWarnings("unchecked")
    private List<OverseasStockQuote> savedQuotes() {
        ArgumentCaptor<Iterable<OverseasStockQuote>> captor = ArgumentCaptor.forClass(Iterable.class);
        verify(repository).saveAll(captor.capture());
        return (List<OverseasStockQuote>) captor.getValue();
    }

    private static OverseasStockQuote lastQuote(String code, String price, String marketValue) {
        return OverseasStockQuote.create(
                new OverseasStockQuoteId("JAPAN", code, LAST_TIME),
                new BigDecimal(price),
                new BigDecimal(price),
                BigDecimal.ZERO,
                new BigDecimal(marketValue));
    }

    private static YahooFinanceQuoteClient.Quote quote(String price, String previousClose, Instant time) {
        return new YahooFinanceQuoteClient.Quote(new BigDecimal(price), new BigDecimal(previousClose), time);
    }
}
