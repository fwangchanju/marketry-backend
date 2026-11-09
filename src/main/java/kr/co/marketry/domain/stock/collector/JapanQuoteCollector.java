package kr.co.marketry.domain.stock.collector;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.function.Consumer;
import kr.co.marketry.common.enums.Zone;
import kr.co.marketry.common.util.KstClock;
import kr.co.marketry.domain.stock.client.YahooFinanceQuoteClient;
import kr.co.marketry.domain.stock.entity.OverseasStockQuote;
import kr.co.marketry.domain.stock.entity.OverseasStockQuoteId;
import kr.co.marketry.domain.stock.repository.OverseasStockQuoteRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClientResponseException;

/**
 * 일본 시세를 야후에서 받아 저장한다. 마지막 시세에 있는 종목을 그대로 받아 같은 종목 구성을 유지한다.
 *
 * <p>차트 응답에 시가총액이 없어 마지막 시세의 시가총액을 가격 변화만큼 늘리거나 줄인다. 받지 못한 종목은 마지막 값을 그대로
 * 둔다. 받은 종목이 너무 적거나 야후가 호출을 제한하면 저장하지 않아 화면은 직전 시세를 유지한다.
 */
@Slf4j
@Component
public class JapanQuoteCollector {
    static final String MARKET = "JAPAN";
    static final double MIN_SUCCESS_RATIO = 0.9;
    private static final Duration REQUEST_INTERVAL = Duration.ofMillis(200);
    private static final Duration OPENING_RETRY_INTERVAL = Duration.ofSeconds(15);
    private static final int SNAPSHOT_GRID_MINUTES = 5;
    private static final int PRICE_SCALE = 2;
    private static final BigDecimal PERCENT = BigDecimal.valueOf(100);
    private static final LocalTime OPENING_MEASUREMENT_CUTOFF = LocalTime.of(9, 18);

    private final YahooFinanceQuoteClient client;
    private final OverseasStockQuoteRepository repository;
    private final Consumer<Duration> sleeper;
    private final Clock clock;

    @Autowired
    public JapanQuoteCollector(YahooFinanceQuoteClient client, OverseasStockQuoteRepository repository) {
        this(client, repository, JapanQuoteCollector::sleep, Clock.system(Zone.KST.zoneId()));
    }

    JapanQuoteCollector(
            YahooFinanceQuoteClient client, OverseasStockQuoteRepository repository, Consumer<Duration> sleeper) {
        this(client, repository, sleeper, Clock.system(Zone.KST.zoneId()));
    }

    JapanQuoteCollector(
            YahooFinanceQuoteClient client,
            OverseasStockQuoteRepository repository,
            Consumer<Duration> sleeper,
            Clock clock) {
        this.client = client;
        this.repository = repository;
        this.sleeper = sleeper;
        this.clock = clock;
    }

    /**
     * 개장 직후 Yahoo가 오늘 시세를 제공하는 시각을 측정한다. 오늘 시세가 아닌 종목만 다시 조회하며 DB에는 저장하지 않는다.
     *
     * <p>정규 수집 시작 직전인 09:18에 끝내고, Yahoo가 429를 반환하면 즉시 중단한다.
     */
    public OpeningMeasurement measureOpeningFreshness() {
        LocalDate today = LocalDateTime.now(clock).toLocalDate();
        LocalDateTime startedAt = LocalDateTime.now(clock);
        LocalDateTime cutoff = today.atTime(OPENING_MEASUREMENT_CUTOFF);
        LocalDateTime lastSnapshotTime = repository
                .findFirstByIdMarketOrderByIdSnapshotTimeDesc(MARKET)
                .map(quote -> quote.getId().getSnapshotTime())
                .orElse(null);
        if (lastSnapshotTime == null) {
            return new OpeningMeasurement(startedAt, LocalDateTime.now(clock), 0, 0, 0, null, null, "기준 시세 없음");
        }

        Set<String> todayCodes = new HashSet<>();
        List<OverseasStockQuote> pending =
                new ArrayList<>(repository.findAllByIdMarketAndIdSnapshotTime(MARKET, lastSnapshotTime));
        int totalCount = pending.size();
        if (totalCount == 0) {
            return new OpeningMeasurement(startedAt, LocalDateTime.now(clock), 0, 0, 0, null, null, "기준 시세 없음");
        }
        int threshold = (int) Math.ceil(totalCount * MIN_SUCCESS_RATIO);
        int requestCount = 0;
        boolean rateLimited = false;
        LocalDateTime firstTodayQuoteAt = null;
        LocalDateTime thresholdReachedAt = null;
        boolean firstPass = true;

        while (todayCodes.size() < threshold && LocalDateTime.now(clock).isBefore(cutoff)) {
            if (firstPass == false) {
                sleeper.accept(OPENING_RETRY_INTERVAL);
                if (LocalDateTime.now(clock).isBefore(cutoff) == false) {
                    break;
                }
            }
            firstPass = false;
            FetchBatch batch = fetchAll(pending, cutoff);
            requestCount += batch.requestCount();
            rateLimited = batch.rateLimited();
            List<OverseasStockQuote> stillPending = new ArrayList<>();
            for (Fetched item : batch.fetched()) {
                if (isTodayQuote(item.quote(), today)) {
                    todayCodes.add(item.last().getId().getStockCode());
                    if (firstTodayQuoteAt == null) {
                        firstTodayQuoteAt = item.observedAt();
                    }
                    if (thresholdReachedAt == null && todayCodes.size() >= threshold) {
                        thresholdReachedAt = item.observedAt();
                    }
                } else {
                    stillPending.add(item.last());
                }
            }
            pending = stillPending;
            if (rateLimited || pending.isEmpty() || LocalDateTime.now(clock).isBefore(cutoff) == false) {
                break;
            }
        }

        String reason = thresholdReachedAt != null ? null : rateLimited ? "Yahoo 호출 제한" : "09:18 이전 90% 미도달";
        return new OpeningMeasurement(
                startedAt,
                LocalDateTime.now(clock),
                todayCodes.size(),
                totalCount,
                requestCount,
                firstTodayQuoteAt,
                thresholdReachedAt,
                reason);
    }

    private static boolean isTodayQuote(YahooFinanceQuoteClient.Quote quote, LocalDate today) {
        return quote != null
                && quote.marketTime().atZone(Zone.KST.zoneId()).toLocalDate().equals(today);
    }

    public Result collect() {
        return collect(KstClock.now().toLocalDate());
    }

    Result collect(LocalDate collectionDate) {
        LocalDateTime lastSnapshotTime = repository
                .findFirstByIdMarketOrderByIdSnapshotTimeDesc(MARKET)
                .map(quote -> quote.getId().getSnapshotTime())
                .orElse(null);
        if (lastSnapshotTime == null) {
            return Result.skipped("기준이 될 일본 시세가 없습니다", 0, 0, 0, 0);
        }

        List<OverseasStockQuote> lastQuotes = repository.findAllByIdMarketAndIdSnapshotTime(MARKET, lastSnapshotTime);
        List<Fetched> fetched = fetchAll(lastQuotes, null).fetched();
        long successCount =
                fetched.stream().filter(item -> item.quote() != null).count();
        int todayQuoteCount = (int) fetched.stream()
                .filter(item -> item.quote() != null)
                .filter(item -> item.quote()
                        .marketTime()
                        .atZone(Zone.KST.zoneId())
                        .toLocalDate()
                        .equals(collectionDate))
                .count();
        int otherDateQuoteCount = (int) successCount - todayQuoteCount;
        int failureCount = fetched.size() - (int) successCount;
        if (successCount < lastQuotes.size() * MIN_SUCCESS_RATIO) {
            return Result.skipped(
                    "받은 종목이 너무 적습니다: " + successCount + "/" + lastQuotes.size(),
                    todayQuoteCount,
                    otherDateQuoteCount,
                    failureCount,
                    lastQuotes.size());
        }

        LocalDateTime snapshotTime = snapshotTimeOf(fetched);
        if (snapshotTime.isAfter(lastSnapshotTime) == false) {
            return Result.skipped("새 시세가 없습니다", todayQuoteCount, otherDateQuoteCount, failureCount, lastQuotes.size());
        }

        List<OverseasStockQuote> quotes =
                fetched.stream().map(item -> toQuote(item, snapshotTime)).toList();
        repository.saveAll(quotes);
        return new Result(
                true,
                snapshotTime,
                (int) successCount,
                quotes.size(),
                todayQuoteCount,
                otherDateQuoteCount,
                failureCount,
                null);
    }

    private FetchBatch fetchAll(List<OverseasStockQuote> lastQuotes, LocalDateTime cutoff) {
        List<Fetched> fetched = new ArrayList<>(lastQuotes.size());
        boolean limited = false;
        int requestCount = 0;
        for (OverseasStockQuote last : lastQuotes) {
            String stockCode = last.getId().getStockCode();
            if (cutoff != null && LocalDateTime.now(clock).isBefore(cutoff) == false) {
                fetched.add(new Fetched(last, null, null));
                continue;
            }
            if (limited) {
                fetched.add(new Fetched(last, null, null));
                continue;
            }
            try {
                YahooFinanceQuoteClient.Quote quote = client.fetchTokyoQuote(stockCode);
                requestCount++;
                fetched.add(new Fetched(last, quote, LocalDateTime.now(clock)));
            } catch (RestClientResponseException failure) {
                requestCount++;
                limited = failure.getStatusCode().isSameCodeAs(HttpStatus.TOO_MANY_REQUESTS);
                log.warn("[일본시세] {} 조회 실패: status={}", stockCode, failure.getStatusCode());
                fetched.add(new Fetched(last, null, LocalDateTime.now(clock)));
            } catch (RuntimeException failure) {
                requestCount++;
                log.warn("[일본시세] {} 조회 실패: {}", stockCode, failure.getMessage());
                fetched.add(new Fetched(last, null, LocalDateTime.now(clock)));
            }
            sleeper.accept(REQUEST_INTERVAL);
        }
        return new FetchBatch(fetched, requestCount, limited);
    }

    // 야후의 체결 시각을 5분 단위로 내려 저장한다. 수집 시각을 쓰면 지연된 시세가 최신처럼 보이기 때문이다.
    private static LocalDateTime snapshotTimeOf(List<Fetched> fetched) {
        Instant latest = fetched.stream()
                .filter(item -> item.quote() != null)
                .map(item -> item.quote().marketTime())
                .max(Instant::compareTo)
                .orElseThrow();
        LocalDateTime time = LocalDateTime.ofInstant(latest, Zone.KST.zoneId());
        return time.withSecond(0)
                .withNano(0)
                .withMinute(time.getMinute() / SNAPSHOT_GRID_MINUTES * SNAPSHOT_GRID_MINUTES);
    }

    private static OverseasStockQuote toQuote(Fetched item, LocalDateTime snapshotTime) {
        OverseasStockQuote last = item.last();
        OverseasStockQuoteId id = new OverseasStockQuoteId(MARKET, last.getId().getStockCode(), snapshotTime);
        if (item.quote() == null) {
            return OverseasStockQuote.create(
                    id, last.getCurrentPrice(), last.getLastPrice(), last.getChangeRate(), last.getTotalMarketValue());
        }
        BigDecimal price = item.quote().currentPrice().setScale(PRICE_SCALE, RoundingMode.HALF_UP);
        BigDecimal previousClose = item.quote().previousClose().setScale(PRICE_SCALE, RoundingMode.HALF_UP);
        return OverseasStockQuote.create(
                id,
                price,
                previousClose,
                changeRateOf(price, previousClose),
                marketValueOf(last.getTotalMarketValue(), last.getCurrentPrice(), price));
    }

    static BigDecimal changeRateOf(BigDecimal price, BigDecimal previousClose) {
        return price.subtract(previousClose).multiply(PERCENT).divide(previousClose, PRICE_SCALE, RoundingMode.HALF_UP);
    }

    static BigDecimal marketValueOf(BigDecimal lastMarketValue, BigDecimal lastPrice, BigDecimal price) {
        return lastMarketValue.multiply(price).divide(lastPrice, 0, RoundingMode.HALF_UP);
    }

    private static void sleep(Duration duration) {
        try {
            Thread.sleep(duration);
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("일본 시세 수집이 중단됐습니다", interrupted);
        }
    }

    private record Fetched(OverseasStockQuote last, YahooFinanceQuoteClient.Quote quote, LocalDateTime observedAt) {}

    private record FetchBatch(List<Fetched> fetched, int requestCount, boolean rateLimited) {}

    public record OpeningMeasurement(
            LocalDateTime startedAt,
            LocalDateTime finishedAt,
            int todayQuoteCount,
            int totalCount,
            int requestCount,
            LocalDateTime firstTodayQuoteAt,
            LocalDateTime thresholdReachedAt,
            String reason) {}

    public record Result(
            boolean saved,
            LocalDateTime snapshotTime,
            int successCount,
            int totalCount,
            int todayQuoteCount,
            int otherDateQuoteCount,
            int failureCount,
            String reason) {
        static Result skipped(
                String reason, int todayQuoteCount, int otherDateQuoteCount, int failureCount, int totalCount) {
            return new Result(
                    false,
                    null,
                    todayQuoteCount + otherDateQuoteCount,
                    totalCount,
                    todayQuoteCount,
                    otherDateQuoteCount,
                    failureCount,
                    reason);
        }
    }
}
