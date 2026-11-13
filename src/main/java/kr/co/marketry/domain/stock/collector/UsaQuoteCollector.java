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
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import kr.co.marketry.domain.stock.client.YahooFinanceQuoteClient;
import kr.co.marketry.domain.stock.client.YahooFinanceQuoteClient.DailyBar;
import kr.co.marketry.domain.stock.client.YahooFinanceQuoteClient.IndexSession;
import kr.co.marketry.domain.stock.client.YahooFinanceQuoteClient.MarketValue;
import kr.co.marketry.domain.stock.collector.UsaConstituents.Constituent;
import kr.co.marketry.domain.stock.entity.OverseasStockQuote;
import kr.co.marketry.domain.stock.entity.OverseasStockQuoteId;
import kr.co.marketry.domain.stock.repository.OverseasStockQuoteRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClientResponseException;

/**
 * 미국 S&amp;P 500 종가를 야후에서 받아 하루에 한 번 저장한다.
 *
 * <p>정규장 종료 시각 + 대기 시간이 지난 뒤에만 저장한다. 종료 시각은 야후가 알려 주는 값이라 조기 마감일도 따른다. 받지 못한
 * 종목은 직전 값을 복사하지 않고 저장하지 않는다. 받은 종목이 90% 미만이거나 야후가 호출을 제한하면 아무것도 저장하지 않는다.
 *
 * <p>시가총액은 직전 스냅샷의 시가총액에 그 날짜부터 오늘까지의 종가 비율을 곱한다. 두 가격이 모두 같은 응답의 분할 반영 값이라 분할일에도
 * 맞고, 직전 스냅샷이 며칠 전이어도 이어진다.
 */
@Slf4j
@Component
public class UsaQuoteCollector {
    static final String MARKET = "USA";
    static final String INDEX_SYMBOL = "^GSPC";
    static final double MIN_SUCCESS_RATIO = 0.9;
    static final String TOO_FEW_REASON = "받은 종목이 너무 적습니다";
    private static final LocalTime CLOSE_TIME = LocalTime.of(16, 0);
    private static final Duration REQUEST_INTERVAL = Duration.ofMillis(200);
    // 새 종목의 시가총액은 야후 기록이 몇 달 전에 멈춰 있을 수 있다. 일봉과 시가총액을 같은 구간으로 받아 기록 날짜의 종가를 찾는다.
    private static final int NEW_STOCK_LOOKBACK_DAYS = 400;
    private static final int PRICE_SCALE = 2;
    private static final BigDecimal PERCENT = BigDecimal.valueOf(100);

    private final YahooFinanceQuoteClient client;
    private final OverseasStockQuoteRepository repository;
    private final UsaConstituents constituents;
    private final Duration delay;
    private final Consumer<Duration> sleeper;
    private final Clock clock;

    @Autowired
    public UsaQuoteCollector(
            YahooFinanceQuoteClient client,
            OverseasStockQuoteRepository repository,
            UsaConstituents constituents,
            @Value("${usa-quote.delay-minutes:30}") int delayMinutes) {
        this(
                client,
                repository,
                constituents,
                Duration.ofMinutes(delayMinutes),
                UsaQuoteCollector::sleep,
                Clock.systemUTC());
    }

    UsaQuoteCollector(
            YahooFinanceQuoteClient client,
            OverseasStockQuoteRepository repository,
            UsaConstituents constituents,
            Duration delay,
            Consumer<Duration> sleeper,
            Clock clock) {
        this.client = client;
        this.repository = repository;
        this.constituents = constituents;
        this.delay = delay;
        this.sleeper = sleeper;
        this.clock = clock;
    }

    public Result collect() {
        IndexSession session = client.fetchIndexSession(INDEX_SYMBOL);
        LocalDate day = session.latestBarDate();
        Instant closeAt = UsaMarketSession.closeTimeOf(session);
        if (closeAt == null) {
            return Result.of(Status.WAITING, day, null, 0, 0, 0, "장 종료 시각이 최신 봉 날짜보다 앞섭니다");
        }
        if (clock.instant().isBefore(closeAt.plus(delay))) {
            return Result.of(Status.WAITING, day, null, 0, 0, 0, "마감 후 대기 중입니다");
        }
        LocalDateTime lastSnapshotTime = repository
                .findFirstByIdMarketOrderByIdSnapshotTimeDesc(MARKET)
                .map(quote -> quote.getId().getSnapshotTime())
                .orElse(null);
        if (lastSnapshotTime == null) {
            return Result.of(Status.NO_BASELINE, day, null, 0, 0, 0, "기준이 될 미국 시세가 없습니다");
        }
        if (lastSnapshotTime.toLocalDate().isBefore(day) == false) {
            return Result.of(Status.ALREADY_SAVED, day, null, 0, 0, 0, "이미 저장했습니다");
        }

        LocalDateTime snapshotTime = day.atTime(CLOSE_TIME);
        Map<String, OverseasStockQuote> lastByCode = new HashMap<>();
        for (OverseasStockQuote quote : repository.findAllByIdMarketAndIdSnapshotTime(MARKET, lastSnapshotTime)) {
            lastByCode.put(quote.getId().getStockCode(), quote);
        }
        List<OverseasStockQuote> rows = new ArrayList<>();
        int totalCount = constituents.all().size();
        boolean limited = false;
        for (Constituent constituent : constituents.all()) {
            if (limited) {
                continue;
            }
            try {
                OverseasStockQuote row = buildRow(
                        constituent.symbol(),
                        day,
                        lastSnapshotTime.toLocalDate(),
                        lastByCode.get(constituent.symbol()),
                        snapshotTime);
                if (row != null) {
                    rows.add(row);
                }
            } catch (RestClientResponseException failure) {
                limited = failure.getStatusCode().isSameCodeAs(HttpStatus.TOO_MANY_REQUESTS);
                log.warn("[미국시세] {} 조회 실패: status={}", constituent.symbol(), failure.getStatusCode());
            } catch (RuntimeException failure) {
                log.warn("[미국시세] {} 조회 실패: {}", constituent.symbol(), failure.getMessage());
            }
        }
        int failureCount = totalCount - rows.size();
        if (rows.size() < totalCount * MIN_SUCCESS_RATIO) {
            String reason = limited ? "야후 호출 제한" : TOO_FEW_REASON + ": " + rows.size() + "/" + totalCount;
            return Result.of(Status.TOO_FEW, day, null, rows.size(), totalCount, failureCount, reason);
        }
        repository.saveAll(rows);
        return Result.of(Status.SAVED, day, snapshotTime, rows.size(), totalCount, failureCount, null);
    }

    /** 오늘 봉이나 기준이 되는 봉을 찾지 못하면 null이다. 그 종목은 저장하지 않는다. */
    private OverseasStockQuote buildRow(
            String symbol, LocalDate day, LocalDate lastDay, OverseasStockQuote last, LocalDateTime snapshotTime) {
        LocalDate from = last == null ? day.minusDays(NEW_STOCK_LOOKBACK_DAYS) : lastDay;
        List<DailyBar> bars = client.fetchDailyBars(symbol, from, day);
        sleeper.accept(REQUEST_INTERVAL);
        DailyBar today = barOn(bars, day);
        DailyBar previous = barBefore(bars, day);
        if (today == null || previous == null) {
            return null;
        }
        BigDecimal baseMarketValue;
        BigDecimal baseClose;
        if (last == null) {
            MarketValue marketValue = client.fetchMarketValueOf(symbol, day.minusDays(NEW_STOCK_LOOKBACK_DAYS), day);
            sleeper.accept(REQUEST_INTERVAL);
            baseMarketValue = marketValue.value();
            baseClose = closeOn(bars, marketValue.date());
        } else {
            DailyBar lastBar = barOn(bars, lastDay);
            if (lastBar == null) {
                return null;
            }
            baseMarketValue = last.getTotalMarketValue();
            baseClose = lastBar.close();
        }
        BigDecimal price = today.close().setScale(PRICE_SCALE, RoundingMode.HALF_UP);
        BigDecimal previousClose = previous.close().setScale(PRICE_SCALE, RoundingMode.HALF_UP);
        return OverseasStockQuote.create(
                new OverseasStockQuoteId(MARKET, symbol, snapshotTime),
                price,
                previousClose,
                changeRateOf(price, previousClose),
                marketValueOf(baseMarketValue, baseClose, today.close()));
    }

    static BigDecimal changeRateOf(BigDecimal price, BigDecimal previousClose) {
        return price.subtract(previousClose).multiply(PERCENT).divide(previousClose, PRICE_SCALE, RoundingMode.HALF_UP);
    }

    static BigDecimal marketValueOf(BigDecimal baseMarketValue, BigDecimal baseClose, BigDecimal price) {
        return baseMarketValue.multiply(price).divide(baseClose, 0, RoundingMode.HALF_UP);
    }

    private static DailyBar barOn(List<DailyBar> bars, LocalDate date) {
        for (DailyBar bar : bars) {
            if (bar.date().equals(date)) {
                return bar;
            }
        }
        return null;
    }

    /** 그 날짜 바로 앞 거래일의 봉. */
    private static DailyBar barBefore(List<DailyBar> bars, LocalDate date) {
        DailyBar found = null;
        for (DailyBar bar : bars) {
            if (bar.date().isBefore(date)) {
                found = bar;
            }
        }
        return found;
    }

    /** 시가총액 날짜의 종가. 그날 봉이 없으면 그 앞의 가장 가까운 봉을 쓰고, 그것도 없으면 첫 봉을 쓴다. */
    static BigDecimal closeOn(List<DailyBar> bars, LocalDate date) {
        BigDecimal close = bars.getFirst().close();
        for (DailyBar bar : bars) {
            if (bar.date().isAfter(date)) {
                break;
            }
            close = bar.close();
        }
        return close;
    }

    private static void sleep(Duration duration) {
        try {
            Thread.sleep(duration);
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("미국 시세 수집이 중단됐습니다", interrupted);
        }
    }

    public enum Status {
        SAVED,
        /** 마감 후 대기 시간이 지나지 않았거나 장 종료 시각이 최신 봉 날짜와 맞지 않는다. */
        WAITING,
        /** 그 날짜의 시세를 이미 저장했다. 휴장일에도 이 상태가 된다. */
        ALREADY_SAVED,
        NO_BASELINE,
        TOO_FEW
    }

    public record Result(
            Status status,
            LocalDate day,
            LocalDateTime snapshotTime,
            int successCount,
            int totalCount,
            int failureCount,
            String reason) {
        static Result of(
                Status status,
                LocalDate day,
                LocalDateTime snapshotTime,
                int successCount,
                int totalCount,
                int failureCount,
                String reason) {
            return new Result(status, day, snapshotTime, successCount, totalCount, failureCount, reason);
        }

        public boolean saved() {
            return status == Status.SAVED;
        }

        /** 야후 요청이 막히거나 실패해 저장하지 못했는지. 대기나 이미 저장은 정상이다. */
        public boolean fetchFailed() {
            return status == Status.TOO_FEW;
        }
    }
}
