package kr.co.marketry.domain.stock.collector;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.function.Consumer;
import kr.co.marketry.domain.stock.client.YahooFinanceQuoteClient;
import kr.co.marketry.domain.stock.client.YahooFinanceQuoteClient.IndexSession;
import kr.co.marketry.domain.stock.client.YahooFinanceQuoteClient.Quote;
import kr.co.marketry.domain.stock.collector.UsaConstituents.Constituent;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClientResponseException;

/**
 * 미국 장 마감 직후 야후가 종목별 종가를 내주는 시각을 측정한다. 저장하지 않고 로그만 남기며, 수집 시작 시각(정규장 종료 + 대기
 * 시간)을 조정할 근거를 모으는 용도다. 일본 개장 측정기와 같은 구조다.
 *
 * <p>종목의 마지막 체결 시각이 정규장 종료 시각 이상이면 그 종목은 확인된 것으로 본다. 500종목의 90%가 확인되면 끝낸다. 정규 수집이
 * 시작되는 시각까지 90%에 닿지 못하거나 야후가 429를 돌려주면 그때 끝낸다.
 */
@Slf4j
@Component
public class UsaCloseMeasurer {
    private static final Duration RETRY_INTERVAL = Duration.ofSeconds(15);
    private static final Duration REQUEST_INTERVAL = Duration.ofMillis(200);

    private final YahooFinanceQuoteClient client;
    private final UsaConstituents constituents;
    private final Duration delay;
    private final Consumer<Duration> sleeper;
    private final Clock clock;
    private LocalDate measuredDay;

    @Autowired
    public UsaCloseMeasurer(
            YahooFinanceQuoteClient client,
            UsaConstituents constituents,
            @Value("${usa-quote.delay-minutes:30}") int delayMinutes) {
        this(client, constituents, Duration.ofMinutes(delayMinutes), UsaCloseMeasurer::sleep, Clock.systemUTC());
    }

    UsaCloseMeasurer(
            YahooFinanceQuoteClient client,
            UsaConstituents constituents,
            Duration delay,
            Consumer<Duration> sleeper,
            Clock clock) {
        this.client = client;
        this.constituents = constituents;
        this.delay = delay;
        this.sleeper = sleeper;
        this.clock = clock;
    }

    public Measurement measure() {
        IndexSession session = client.fetchIndexSession(UsaQuoteCollector.INDEX_SYMBOL);
        LocalDate day = session.latestBarDate();
        Instant closeAt = UsaMarketSession.closeTimeOf(session);
        Instant startedAt = clock.instant();
        if (closeAt == null) {
            return Measurement.skipped(day, startedAt, "장 종료 시각이 최신 봉 날짜보다 앞섭니다");
        }
        Instant cutoff = closeAt.plus(delay);
        if (day.equals(measuredDay)) {
            return Measurement.skipped(day, startedAt, "이미 측정했습니다");
        }
        if (startedAt.isBefore(closeAt)) {
            return Measurement.skipped(day, startedAt, "마감 전입니다");
        }
        if (startedAt.isBefore(cutoff) == false) {
            return Measurement.skipped(day, startedAt, "측정 구간이 지났습니다");
        }
        measuredDay = day;

        int totalCount = constituents.all().size();
        int threshold = (int) Math.ceil(totalCount * UsaQuoteCollector.MIN_SUCCESS_RATIO);
        List<String> pending = new ArrayList<>();
        for (Constituent constituent : constituents.all()) {
            pending.add(constituent.symbol());
        }
        Set<String> confirmed = new HashSet<>();
        int requestCount = 0;
        boolean limited = false;
        Instant firstConfirmedAt = null;
        Instant thresholdReachedAt = null;
        boolean firstPass = true;

        while (confirmed.size() < threshold && clock.instant().isBefore(cutoff)) {
            if (firstPass == false) {
                sleeper.accept(RETRY_INTERVAL);
                if (clock.instant().isBefore(cutoff) == false) {
                    break;
                }
            }
            firstPass = false;
            List<String> stillPending = new ArrayList<>();
            for (String symbol : pending) {
                if (limited) {
                    stillPending.add(symbol);
                    continue;
                }
                try {
                    Quote quote = client.fetchQuote(symbol);
                    requestCount++;
                    if (quote.marketTime().isBefore(closeAt)) {
                        stillPending.add(symbol);
                    } else {
                        confirmed.add(symbol);
                        Instant observedAt = clock.instant();
                        if (firstConfirmedAt == null) {
                            firstConfirmedAt = observedAt;
                        }
                        if (thresholdReachedAt == null && confirmed.size() >= threshold) {
                            thresholdReachedAt = observedAt;
                        }
                    }
                } catch (RestClientResponseException failure) {
                    requestCount++;
                    limited = failure.getStatusCode().isSameCodeAs(HttpStatus.TOO_MANY_REQUESTS);
                    stillPending.add(symbol);
                    log.warn("[미국종가측정] {} 조회 실패: status={}", symbol, failure.getStatusCode());
                } catch (RuntimeException failure) {
                    requestCount++;
                    stillPending.add(symbol);
                    log.warn("[미국종가측정] {} 조회 실패: {}", symbol, failure.getMessage());
                }
                sleeper.accept(REQUEST_INTERVAL);
            }
            pending = stillPending;
            if (limited || pending.isEmpty()) {
                break;
            }
        }

        String reason = null;
        if (thresholdReachedAt == null) {
            reason = limited ? "야후 호출 제한" : "정규 수집 시작 전 90% 미도달";
        }
        return new Measurement(
                day,
                closeAt,
                startedAt,
                clock.instant(),
                confirmed.size(),
                totalCount,
                requestCount,
                firstConfirmedAt,
                thresholdReachedAt,
                reason);
    }

    private static void sleep(Duration duration) {
        try {
            Thread.sleep(duration);
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("미국 종가 측정이 중단됐습니다", interrupted);
        }
    }

    public record Measurement(
            LocalDate day,
            Instant closeAt,
            Instant startedAt,
            Instant finishedAt,
            int confirmedCount,
            int totalCount,
            int requestCount,
            Instant firstConfirmedAt,
            Instant thresholdReachedAt,
            String reason) {
        static Measurement skipped(LocalDate day, Instant now, String reason) {
            return new Measurement(day, null, now, now, 0, 0, 0, null, null, reason);
        }

        /** 측정을 건너뛴 경우(마감 전, 이미 측정함 등)인지. */
        public boolean wasSkipped() {
            return closeAt == null;
        }
    }
}
