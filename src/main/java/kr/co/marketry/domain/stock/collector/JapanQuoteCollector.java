package kr.co.marketry.domain.stock.collector;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import kr.co.marketry.common.enums.Zone;
import kr.co.marketry.common.util.KstClock;
import kr.co.marketry.domain.stock.client.YahooFinanceQuoteClient;
import kr.co.marketry.domain.stock.entity.OverseasIndexQuote;
import kr.co.marketry.domain.stock.entity.OverseasIndexQuoteId;
import kr.co.marketry.domain.stock.entity.OverseasStockQuote;
import kr.co.marketry.domain.stock.entity.OverseasStockQuoteId;
import kr.co.marketry.domain.stock.repository.OverseasIndexQuoteRepository;
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
    static final String INDEX_MARKET = "NIKKEI225";
    private static final String INDEX_SYMBOL = "^N225";
    static final double MIN_SUCCESS_RATIO = 0.9;
    private static final Duration REQUEST_INTERVAL = Duration.ofMillis(200);
    private static final int SNAPSHOT_GRID_MINUTES = 5;
    private static final int PRICE_SCALE = 2;
    private static final BigDecimal PERCENT = BigDecimal.valueOf(100);

    private final YahooFinanceQuoteClient client;
    private final OverseasStockQuoteRepository repository;
    private final OverseasIndexQuoteRepository indexQuotes;
    private final Consumer<Duration> sleeper;

    @Autowired
    public JapanQuoteCollector(
            YahooFinanceQuoteClient client,
            OverseasStockQuoteRepository repository,
            OverseasIndexQuoteRepository indexQuotes) {
        this(client, repository, indexQuotes, JapanQuoteCollector::sleep);
    }

    JapanQuoteCollector(
            YahooFinanceQuoteClient client,
            OverseasStockQuoteRepository repository,
            OverseasIndexQuoteRepository indexQuotes,
            Consumer<Duration> sleeper) {
        this.client = client;
        this.repository = repository;
        this.indexQuotes = indexQuotes;
        this.sleeper = sleeper;
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
        List<Fetched> fetched = fetchAll(lastQuotes);
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
                    TOO_FEW_REASON + ": " + successCount + "/" + lastQuotes.size(),
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
        saveIndexQuote(snapshotTime);
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

    /**
     * 닛케이 225 지수의 지금 값을 종목 시세와 같은 시각으로 저장한다. 종목 시세를 먼저 저장한 뒤에 하며, 지수를 받지 못해도 종목 시세는
     * 그대로 둔다. 그 시각의 지수가 없으면 그래프가 지수 막대만 만들지 않는다.
     */
    private void saveIndexQuote(LocalDateTime snapshotTime) {
        try {
            YahooFinanceQuoteClient.Quote quote = client.fetchQuote(INDEX_SYMBOL);
            if (quote == null) {
                log.warn("[일본지수] 지수 값을 받지 못했습니다: {}", snapshotTime);
                return;
            }
            BigDecimal value = quote.currentPrice().setScale(PRICE_SCALE, RoundingMode.HALF_UP);
            BigDecimal previousClose = quote.previousClose().setScale(PRICE_SCALE, RoundingMode.HALF_UP);
            indexQuotes.save(OverseasIndexQuote.create(
                    new OverseasIndexQuoteId(INDEX_MARKET, snapshotTime),
                    value,
                    previousClose,
                    changeRateOf(value, previousClose)));
        } catch (RuntimeException failure) {
            log.warn("[일본지수] 지수 저장 실패: {} {}", snapshotTime, failure.getMessage());
        }
    }

    private List<Fetched> fetchAll(List<OverseasStockQuote> lastQuotes) {
        List<Fetched> fetched = new ArrayList<>(lastQuotes.size());
        boolean limited = false;
        for (OverseasStockQuote last : lastQuotes) {
            String stockCode = last.getId().getStockCode();
            if (limited) {
                fetched.add(new Fetched(last, null));
                continue;
            }
            try {
                fetched.add(new Fetched(last, client.fetchTokyoQuote(stockCode)));
            } catch (RestClientResponseException failure) {
                limited = failure.getStatusCode().isSameCodeAs(HttpStatus.TOO_MANY_REQUESTS);
                log.warn("[일본시세] {} 조회 실패: status={}", stockCode, failure.getStatusCode());
                fetched.add(new Fetched(last, null));
            } catch (RuntimeException failure) {
                log.warn("[일본시세] {} 조회 실패: {}", stockCode, failure.getMessage());
                fetched.add(new Fetched(last, null));
            }
            sleeper.accept(REQUEST_INTERVAL);
        }
        return fetched;
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

    private record Fetched(OverseasStockQuote last, YahooFinanceQuoteClient.Quote quote) {}

    /** 응답을 받은 종목이 기준보다 적어 저장을 건너뛴 이유 — 야후 요청이 막히거나 실패한 상태다. */
    static final String TOO_FEW_REASON = "받은 종목이 너무 적습니다";

    public record Result(
            boolean saved,
            LocalDateTime snapshotTime,
            int successCount,
            int totalCount,
            int todayQuoteCount,
            int otherDateQuoteCount,
            int failureCount,
            String reason) {
        /** 야후 요청이 실패해 저장하지 못했는지. "새 시세가 없습니다"처럼 정상인 건너뜀은 아니다. */
        public boolean fetchFailed() {
            return saved == false && reason != null && reason.startsWith(TOO_FEW_REASON);
        }

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
