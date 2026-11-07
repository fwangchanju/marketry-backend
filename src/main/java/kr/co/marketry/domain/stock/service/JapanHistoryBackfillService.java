package kr.co.marketry.domain.stock.service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Clock;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;
import kr.co.marketry.common.exception.BadRequestException;
import kr.co.marketry.common.exception.ErrorCode;
import kr.co.marketry.domain.stock.client.YahooFinanceQuoteClient;
import kr.co.marketry.domain.stock.client.YahooFinanceQuoteClient.DailyBar;
import kr.co.marketry.domain.stock.client.YahooFinanceQuoteClient.MarketValue;
import kr.co.marketry.domain.stock.entity.HistoryBackfillProgress;
import kr.co.marketry.domain.stock.entity.MarketIndexDaily;
import kr.co.marketry.domain.stock.entity.OverseasStockQuote;
import kr.co.marketry.domain.stock.entity.OverseasStockQuoteId;
import kr.co.marketry.domain.stock.repository.HistoryBackfillProgressRepository;
import kr.co.marketry.domain.stock.repository.MarketIndexDailyRepository;
import kr.co.marketry.domain.stock.repository.OverseasStockQuoteRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * 일본 종목과 닛케이 지수의 과거 값을 야후에서 받는다. 관리자가 시작할 때만 돈다.
 *
 * <p>과거 시가총액은 날짜별로 없어, 야후의 최신 시가총액을 주가 비율로 환산한다. 그 날짜 15:30 행은 야후 값으로 덮어쓰고, 이후 5분
 * 시세의 시가총액도 같은 기준으로 맞춰 다음 수집이 옛 기준을 이어 가지 않게 한다.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class JapanHistoryBackfillService {
    static final String MARKET = "JAPAN";
    static final String INDEX_MARKET = "NIKKEI225";
    private static final String INDEX_SYMBOL = "^N225";
    private static final String TOKYO_SUFFIX = ".T";
    private static final String JOB = "JAPAN";
    private static final LocalTime CLOSE_TIME = LocalTime.of(15, 30);
    private static final Duration CALL_INTERVAL = Duration.ofSeconds(1);
    private static final int LOOKBACK_DAYS = 14;
    private static final int MARKET_VALUE_LOOKBACK_DAYS = 400;
    private static final int MAX_CONSECUTIVE_FAILURES = 20;
    private static final int PRICE_SCALE = 2;
    private static final BigDecimal PERCENT = BigDecimal.valueOf(100);

    private final YahooFinanceQuoteClient client;
    private final OverseasStockQuoteRepository quotes;
    private final MarketIndexDailyRepository indexes;
    private final HistoryBackfillProgressRepository progress;
    private final Clock clock = Clock.system(java.time.ZoneId.of("Asia/Seoul"));
    private final AtomicReference<Status> status =
            new AtomicReference<>(new Status(false, null, null, 0, 0, 0, 0, 0, null, null));

    public Status status() {
        return status.get();
    }

    public Status start(LocalDate from, LocalDate to) {
        if (from.isAfter(to)) {
            throw new BadRequestException(ErrorCode.INVALID_INPUT, "시작일이 종료일보다 늦습니다");
        }
        Status starting = new Status(true, from, to, 0, 0, 0, 0, 0, null, null);
        Status previous = status.getAndUpdate(current -> current.running() ? current : starting);
        if (previous.running()) {
            return previous;
        }
        Thread worker = new Thread(() -> run(from, to), "japan-history-backfill");
        worker.setDaemon(true);
        worker.start();
        return starting;
    }

    void run(LocalDate from, LocalDate to) {
        List<String> items = new ArrayList<>();
        items.add(INDEX_MARKET);
        quotes.findFirstByIdMarketOrderByIdSnapshotTimeDesc(MARKET)
                .ifPresent(latest -> quotes.findAllByIdMarketAndIdSnapshotTime(
                                MARKET, latest.getId().getSnapshotTime())
                        .forEach(quote -> items.add(quote.getId().getStockCode())));
        String key = JOB + ":" + from;
        int done = 0;
        int skipped = 0;
        int failed = 0;
        int consecutiveFailures = 0;
        long savedRows = 0;
        String stopReason = null;
        for (String item : items) {
            if (progress.existsById(new HistoryBackfillProgress.Id(key, item))) {
                skipped++;
                continue;
            }
            status.set(new Status(true, from, to, items.size(), done, skipped, failed, savedRows, item, null));
            try {
                int saved = INDEX_MARKET.equals(item) ? backfillIndex(from, to) : backfillStock(item, from, to);
                progress.save(HistoryBackfillProgress.create(key, item, saved, from, LocalDateTime.now(clock)));
                savedRows += saved;
                done++;
                consecutiveFailures = 0;
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                stopReason = "중단됨";
                break;
            } catch (RuntimeException e) {
                failed++;
                consecutiveFailures++;
                log.warn(
                        "[일본 과거 데이터 실패] | context : {}|{}|{}",
                        item,
                        e.getClass().getSimpleName(),
                        e.getMessage());
                if (consecutiveFailures >= MAX_CONSECUTIVE_FAILURES) {
                    stopReason = "연속 실패 " + consecutiveFailures + "회로 멈춤";
                    break;
                }
            }
        }
        log.info("[일본 과거 데이터 종료] | context : {}|{}|{}|{}|{}", done, skipped, failed, savedRows, stopReason);
        status.set(new Status(false, from, to, items.size(), done, skipped, failed, savedRows, null, stopReason));
    }

    private int backfillIndex(LocalDate from, LocalDate to) throws InterruptedException {
        List<DailyBar> bars = client.fetchDailyBars(INDEX_SYMBOL, from, to);
        pause(CALL_INTERVAL);
        LocalDateTime now = LocalDateTime.now(clock);
        indexes.saveAll(bars.stream()
                .map(bar -> MarketIndexDaily.create(
                        INDEX_MARKET,
                        bar.date(),
                        price(bar.open()),
                        price(bar.high()),
                        price(bar.low()),
                        price(bar.close()),
                        bar.volume(),
                        null,
                        now))
                .toList());
        return bars.size();
    }

    private int backfillStock(String stockCode, LocalDate from, LocalDate to) throws InterruptedException {
        List<DailyBar> bars = client.fetchDailyBars(stockCode + TOKYO_SUFFIX, from.minusDays(LOOKBACK_DAYS), to);
        pause(CALL_INTERVAL);
        MarketValue marketValue =
                client.fetchLatestMarketValue(stockCode, to.minusDays(MARKET_VALUE_LOOKBACK_DAYS), to);
        pause(CALL_INTERVAL);
        BigDecimal baseClose = closeOn(bars, marketValue.date());
        List<OverseasStockQuote> rows = toQuotes(stockCode, bars, marketValue.value(), baseClose, from);
        quotes.saveAll(rows);
        // 이미 쌓인 이후 5분 시세의 시가총액도 같은 기준으로 맞춘다.
        List<OverseasStockQuote> later =
                quotes.findAllByIdMarketAndIdStockCodeAndIdSnapshotTimeAfter(MARKET, stockCode, to.atTime(CLOSE_TIME));
        later.forEach(quote ->
                quote.changeMarketValue(marketValueOf(marketValue.value(), baseClose, quote.getCurrentPrice())));
        quotes.saveAll(later);
        return rows.size();
    }

    /** 시작일 앞의 값은 전일 종가로만 쓴다. 전일 종가가 없는 첫날(상장일)은 등락률을 알 수 없어 저장하지 않는다. */
    static List<OverseasStockQuote> toQuotes(
            String stockCode, List<DailyBar> bars, BigDecimal latestValue, BigDecimal baseClose, LocalDate from) {
        List<OverseasStockQuote> rows = new ArrayList<>();
        for (int index = 1; index < bars.size(); index++) {
            DailyBar bar = bars.get(index);
            if (bar.date().isBefore(from)) {
                continue;
            }
            BigDecimal close = price(bar.close());
            BigDecimal previous = price(bars.get(index - 1).close());
            rows.add(OverseasStockQuote.create(
                    new OverseasStockQuoteId(MARKET, stockCode, bar.date().atTime(CLOSE_TIME)),
                    close,
                    previous,
                    close.subtract(previous).multiply(PERCENT).divide(previous, PRICE_SCALE, RoundingMode.HALF_UP),
                    marketValueOf(latestValue, baseClose, close)));
        }
        return rows;
    }

    /** 시가총액 날짜의 종가. 그날 봉이 없으면 그 앞의 가장 가까운 봉을 쓰고, 그것도 없으면 첫 봉을 쓴다. */
    static BigDecimal closeOn(List<DailyBar> bars, LocalDate date) {
        if (bars.isEmpty()) {
            throw new IllegalStateException("일봉이 없습니다");
        }
        BigDecimal close = bars.getFirst().close();
        for (DailyBar bar : bars) {
            if (bar.date().isAfter(date)) {
                break;
            }
            close = bar.close();
        }
        return close;
    }

    static BigDecimal marketValueOf(BigDecimal latestValue, BigDecimal baseClose, BigDecimal price) {
        return latestValue.multiply(price).divide(baseClose, 0, RoundingMode.HALF_UP);
    }

    private static BigDecimal price(BigDecimal value) {
        return value.setScale(PRICE_SCALE, RoundingMode.HALF_UP);
    }

    void pause(Duration duration) throws InterruptedException {
        Thread.sleep(duration);
    }

    public record Status(
            boolean running,
            LocalDate from,
            LocalDate to,
            int total,
            int done,
            int skipped,
            int failed,
            long savedRows,
            String currentItem,
            String stopReason) {}
}
