package dev.eolmae.marketry.domain.stock.collector;

import dev.eolmae.marketry.common.enums.Country;
import dev.eolmae.marketry.common.enums.Zone;
import dev.eolmae.marketry.common.exception.BadRequestException;
import dev.eolmae.marketry.common.exception.ErrorCode;
import dev.eolmae.marketry.domain.stock.client.StockDailyPriceClient;
import dev.eolmae.marketry.domain.stock.dto.MissingDailyPriceStock;
import dev.eolmae.marketry.domain.stock.enums.ExchangeType;
import dev.eolmae.marketry.domain.stock.enums.MarketCalendarStatus;
import dev.eolmae.marketry.domain.stock.service.MarketCalendarService;
import dev.eolmae.marketry.domain.stock.service.StockDailyPriceService;
import dev.eolmae.marketry.domain.stock.service.StockInfoCacheService;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@Profile("prod")
public class StockDailyPriceCollector {
    private static final List<Duration> REQUEST_INTERVALS =
            List.of(Duration.ofMillis(200), Duration.ofMillis(500), Duration.ofSeconds(2));
    // 토큰·HTTP·DB와 최종 누락 조회를 끝낼 여유다. 기한 직전 새 작업을 시작하지 않는다.
    private static final Duration FINISH_RESERVE = Duration.ofSeconds(45);
    private final StockDailyPriceClient client;
    private final StockDailyPriceService prices;
    private final StockInfoCacheService stocks;
    private final MarketCalendarService calendars;
    private final Clock clock;
    private final Sleeper sleeper;

    @Autowired
    public StockDailyPriceCollector(
            StockDailyPriceClient client,
            StockDailyPriceService prices,
            StockInfoCacheService stocks,
            MarketCalendarService calendars) {
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
            MarketCalendarService calendars,
            Clock clock,
            Sleeper sleeper) {
        this.client = client;
        this.prices = prices;
        this.stocks = stocks;
        this.calendars = calendars;
        this.clock = clock;
        this.sleeper = sleeper;
    }

    /** 스케줄과 날짜 지정 수동 재처리가 공통으로 호출하며, 대상 날짜만 저장한다. */
    public Result collect(LocalDate targetDate, List<String> selectedCodes, Instant deadline) {
        return collect(targetDate, selectedCodes, deadline, missing -> {});
    }

    public Result collect(
            LocalDate targetDate,
            List<String> selectedCodes,
            Instant deadline,
            Consumer<List<MissingDailyPriceStock>> progress) {
        if (targetDate.isBefore(LocalDate.now(clock)) == false) {
            throw new BadRequestException(ErrorCode.INVALID_INPUT, "일봉 대상 날짜는 실행일 이전");
        }
        var calendar = calendars.findByCountryAndDate(Country.KR, targetDate);
        if (calendar.isPresent() && calendar.get().getStatus() == MarketCalendarStatus.HOLIDAY) {
            return new Result(Status.HOLIDAY, 0, 0, 0, List.of());
        }
        if (calendar.isEmpty() || calendar.get().getStatus() != MarketCalendarStatus.TRADING_DAY) {
            return new Result(Status.CALENDAR_UNAVAILABLE, 0, 0, 0, List.of());
        }
        List<String> targetCodes = stocks.getCache().values().stream()
                .filter(stock -> stock.isActiveAndOrdinary()
                        && (selectedCodes.isEmpty() || selectedCodes.contains(stock.getStockCode())))
                .map(stock -> stock.getStockCode())
                .sorted()
                .toList();
        if (targetCodes.isEmpty()
                || (selectedCodes.isEmpty() == false && targetCodes.containsAll(selectedCodes) == false)) {
            throw new BadRequestException(ErrorCode.INVALID_INPUT, "활성 일반주 대상 종목 없음 또는 잘못된 코드");
        }
        List<MissingDailyPriceStock> missing = prices.findMissing(targetDate, targetCodes);
        progress.accept(missing);
        int passes = 0;
        int attempts = 0;
        int inserted = 0;
        Instant previousRequest = null;
        boolean stopped = false;
        // 두 번째부터는 DB가 반환한 누락 종목·거래소만 다시 요청한다.
        for (Duration interval : REQUEST_INTERVALS) {
            if (missing.isEmpty() || stopped) {
                break;
            }
            passes++;
            for (var stock : missing) {
                for (ExchangeType exchange : List.of(ExchangeType.KRX, ExchangeType.NXT)) {
                    if ((exchange == ExchangeType.KRX && stock.krxMissing() == false)
                            || (exchange == ExchangeType.NXT && stock.nxtMissing() == false)) {
                        continue;
                    }
                    if (waitForRequest(previousRequest, interval, deadline) == false) {
                        stopped = true;
                        break;
                    }
                    previousRequest = clock.instant();
                    attempts++;
                    inserted += collectSafely(stock.stockCode(), exchange, targetDate, deadline);
                }
                if (stopped) {
                    break;
                }
            }
            // 저장된 일봉이 성공 판정의 근거다. 빈 응답이나 저장 실패는 성공으로 처리하지 않는다.
            missing = prices.findMissing(targetDate, targetCodes);
            progress.accept(missing);
            log.info("[일봉 수집 회차 완료] | context : {}|{}|{}|{}", targetDate, passes, attempts, missing.size());
        }
        return new Result(
                missing.isEmpty() ? Status.COMPLETE : stopped ? Status.DEADLINE : Status.INCOMPLETE,
                passes,
                attempts,
                inserted,
                missing);
    }

    private boolean waitForRequest(Instant previous, Duration interval, Instant deadline) {
        if (Thread.currentThread().isInterrupted()
                || clock.instant().plus(FINISH_RESERVE).isBefore(deadline) == false) {
            return false;
        }
        if (previous != null) {
            Duration wait = Duration.between(clock.instant(), previous.plus(interval));
            if (wait.isPositive()) {
                try {
                    sleeper.sleep(wait);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    return false;
                }
            }
        }
        return Thread.currentThread().isInterrupted() == false
                && clock.instant().plus(FINISH_RESERVE).isBefore(deadline);
    }

    private int collectSafely(String stockCode, ExchangeType exchange, LocalDate date, Instant deadline) {
        try {
            var rows = client.fetchFirstPage(stockCode, exchange, date, deadline).stream()
                    .filter(row -> date.format(java.time.format.DateTimeFormatter.BASIC_ISO_DATE)
                            .equals(String.valueOf(row.get("dt"))))
                    .toList();
            var selected = client.parsePrices(stockCode, exchange, rows, LocalDateTime.now(clock));
            if (Thread.currentThread().isInterrupted() || clock.instant().isBefore(deadline) == false) {
                return 0;
            }
            return prices.savePage(selected).inserted();
        } catch (RuntimeException failure) {
            log.warn(
                    "[일봉 종목 수집 실패] | context : {}|{}|{}|{}",
                    date,
                    stockCode,
                    exchange,
                    failure.getClass().getSimpleName());
            return 0;
        }
    }

    @FunctionalInterface
    public interface Sleeper {
        void sleep(Duration duration) throws InterruptedException;
    }

    public enum Status {
        COMPLETE,
        HOLIDAY,
        CALENDAR_UNAVAILABLE,
        DEADLINE,
        INCOMPLETE
    }

    public record Result(Status status, int passes, int attempts, int inserted, List<MissingDailyPriceStock> missing) {}
}
