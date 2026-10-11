package dev.eolmae.marketry.domain.stock.collector;

import dev.eolmae.marketry.common.enums.Zone;
import dev.eolmae.marketry.common.exception.BadRequestException;
import dev.eolmae.marketry.common.exception.ErrorCode;
import dev.eolmae.marketry.domain.stock.client.StockDailyPriceClient;
import dev.eolmae.marketry.domain.stock.entity.StockDailyPrice;
import dev.eolmae.marketry.domain.stock.entity.StockInfo;
import dev.eolmae.marketry.domain.stock.enums.ExchangeType;
import dev.eolmae.marketry.domain.stock.service.MarketCalendarTimeService;
import dev.eolmae.marketry.domain.stock.service.StockDailyPriceService;
import dev.eolmae.marketry.domain.stock.service.StockInfoCacheService;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.OptionalInt;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@Profile("prod")
public class StockDailyPriceCollector {
    // 회차마다 요청 시작 간격을 넓힌다. KiwoomApiClient의 공유 게이트(200ms)는 직전 요청 시작 시각 기준이라 이 간격과 겹쳐서 적용되며 더해지지 않는다.
    private static final List<Duration> REQUEST_INTERVALS =
            List.of(Duration.ofMillis(200), Duration.ofMillis(500), Duration.ofSeconds(2));

    private final StockDailyPriceClient client;
    private final StockDailyPriceService prices;
    private final StockInfoCacheService stocks;
    private final MarketCalendarTimeService calendars;
    private final Clock clock;
    private final Sleeper sleeper;

    @Autowired
    public StockDailyPriceCollector(
            StockDailyPriceClient client,
            StockDailyPriceService prices,
            StockInfoCacheService stocks,
            MarketCalendarTimeService calendars) {
        this(
                client,
                prices,
                stocks,
                calendars,
                Clock.system(Zone.KST.zoneId()),
                duration -> TimeUnit.NANOSECONDS.sleep(duration.toNanos()));
    }

    public StockDailyPriceCollector(
            StockDailyPriceClient client,
            StockDailyPriceService prices,
            StockInfoCacheService stocks,
            MarketCalendarTimeService calendars,
            Clock clock,
            Sleeper sleeper) {
        this.client = client;
        this.prices = prices;
        this.stocks = stocks;
        this.calendars = calendars;
        this.clock = clock;
        this.sleeper = sleeper;
    }

    /**
     * 스케줄과 날짜 지정 수동 재처리가 공통으로 호출하며, 대상 날짜 행만 저장한다. deadline 이상이 되면 새 요청을 시작하지 않고, 이미
     * 시작한 요청 하나는 끝까지 기다린다.
     */
    public Result collect(LocalDate targetDate, List<String> selectedCodes, Instant deadline) {
        if (targetDate.isBefore(LocalDate.now(clock)) == false) {
            throw new BadRequestException(ErrorCode.INVALID_INPUT, "일봉 대상 날짜는 실행일 이전");
        }
        if (calendars.isTradingDay(targetDate) == false) {
            return new Result(Status.HOLIDAY, 0, 0, 0, List.of());
        }
        Map<String, StockInfo> targetStocks = new LinkedHashMap<>();
        stocks.getCache().values().stream()
                .filter(stock -> stock.isActiveAndOrdinary()
                        && (selectedCodes.isEmpty() || selectedCodes.contains(stock.getStockCode())))
                .sorted(Comparator.comparing(StockInfo::getStockCode))
                .forEach(stock -> targetStocks.put(stock.getStockCode(), stock));
        if (targetStocks.isEmpty()
                || (selectedCodes.isEmpty() == false && targetStocks.keySet().containsAll(selectedCodes) == false)) {
            throw new BadRequestException(ErrorCode.INVALID_INPUT, "활성 일반주 대상 종목 없음 또는 잘못된 코드");
        }
        // 실패 목록이 곧 남은 요청이다. 처음에는 전체 KRX와 nxtEnabled 종목의 NXT가 들어 있고, 성공하거나 정상 skip이면 뺀다.
        Set<DailyPriceKey> remaining = new LinkedHashSet<>();
        for (StockInfo stock : targetStocks.values()) {
            remaining.add(new DailyPriceKey(stock.getStockCode(), ExchangeType.KRX));
            if (stock.isNxtEnabled()) {
                remaining.add(new DailyPriceKey(stock.getStockCode(), ExchangeType.NXT));
            }
        }
        int passes = 0;
        int attempts = 0;
        int inserted = 0;
        Instant previousRequest = null;
        for (Duration interval : REQUEST_INTERVALS) {
            if (remaining.isEmpty()) {
                break;
            }
            passes++;
            for (DailyPriceKey key : List.copyOf(remaining)) {
                if (waitForRequest(previousRequest, interval) == false) {
                    return new Result(Status.INTERRUPTED, passes, attempts, inserted, List.of());
                }
                if (clock.instant().isBefore(deadline) == false) {
                    log.warn("[일봉 수집 제한 시간 도달] | context : {}|{}|{}", targetDate, passes, attempts);
                    return new Result(Status.DEADLINE, passes, attempts, inserted, List.of());
                }
                previousRequest = clock.instant();
                attempts++;
                OptionalInt saved = collectSafely(key, targetDate);
                if (saved.isPresent()) {
                    remaining.remove(key);
                    inserted += saved.getAsInt();
                }
            }
            log.info("[일봉 수집 회차 완료] | context : {}|{}|{}|{}", targetDate, passes, attempts, remaining.size());
        }
        return new Result(
                remaining.isEmpty() ? Status.COMPLETE : Status.INCOMPLETE,
                passes,
                attempts,
                inserted,
                toFailedStocks(remaining, targetStocks));
    }

    private boolean waitForRequest(Instant previousRequest, Duration interval) {
        if (Thread.currentThread().isInterrupted()) {
            return false;
        }
        if (previousRequest == null) {
            return true;
        }
        Duration wait = Duration.between(clock.instant(), previousRequest.plus(interval));
        if (wait.isPositive()) {
            try {
                sleeper.sleep(wait);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return false;
            }
        }
        return true;
    }

    /** 저장했거나(skipped 포함) 정상 skip이면 저장 건수를, 재시도할 실패이면 빈 값을 반환한다. */
    private OptionalInt collectSafely(DailyPriceKey key, LocalDate date) {
        List<StockDailyPrice> selected;
        try {
            var rows = client.fetchFirstPage(key.stockCode(), key.exchange(), date).stream()
                    .filter(row -> date.format(DateTimeFormatter.BASIC_ISO_DATE).equals(String.valueOf(row.get("dt"))))
                    .toList();
            if (rows.isEmpty()) {
                // 빈 placeholder나 빈 목록, 대상 날짜 행 없음은 정상 응답이다. 날짜를 만들어 저장하거나 재시도하지 않는다.
                return OptionalInt.of(0);
            }
            selected = client.parsePrices(key.stockCode(), key.exchange(), rows, LocalDateTime.now(clock));
        } catch (RuntimeException failure) {
            log.warn(
                    "[일봉 종목 수집 실패] | context : {}|{}|{}|{}",
                    date,
                    key.stockCode(),
                    key.exchange(),
                    failure.getClass().getSimpleName());
            return OptionalInt.empty();
        }
        try {
            return OptionalInt.of(prices.savePage(selected).inserted());
        } catch (RuntimeException failure) {
            // 인증 정보나 원인 예외 없이 저장하려던 대상 날짜의 가격 행만 남긴다.
            StockDailyPrice price = selected.getFirst();
            log.warn(
                    "[일봉 저장 실패] | context : {}|{}|{}|open={}|high={}|low={}|close={}|volume={}|{}",
                    date,
                    key.stockCode(),
                    key.exchange(),
                    price.getOpenPrice().toPlainString(),
                    price.getHighPrice().toPlainString(),
                    price.getLowPrice().toPlainString(),
                    price.getClosePrice().toPlainString(),
                    price.getVolume(),
                    failure.getClass().getSimpleName());
            return OptionalInt.empty();
        }
    }

    private List<FailedStock> toFailedStocks(Set<DailyPriceKey> remaining, Map<String, StockInfo> targetStocks) {
        // 같은 종목의 KRX/NXT 실패는 한 항목으로 묶는다.
        Map<String, List<ExchangeType>> exchangesByCode = new LinkedHashMap<>();
        for (DailyPriceKey key : remaining) {
            exchangesByCode
                    .computeIfAbsent(key.stockCode(), code -> new ArrayList<>())
                    .add(key.exchange());
        }
        return exchangesByCode.entrySet().stream()
                .map(entry -> new FailedStock(
                        entry.getKey(), targetStocks.get(entry.getKey()).getStockName(), entry.getValue()))
                .toList();
    }

    @FunctionalInterface
    public interface Sleeper {
        void sleep(Duration duration) throws InterruptedException;
    }

    public enum Status {
        COMPLETE,
        HOLIDAY,
        DEADLINE,
        INTERRUPTED,
        INCOMPLETE
    }

    /** failures는 세 회차가 끝나도 남은 종목이며 INCOMPLETE일 때만 채워진다. */
    public record Result(Status status, int passes, int attempts, int inserted, List<FailedStock> failures) {}

    public record FailedStock(String stockCode, String stockName, List<ExchangeType> exchanges) {}

    private record DailyPriceKey(String stockCode, ExchangeType exchange) {}
}
