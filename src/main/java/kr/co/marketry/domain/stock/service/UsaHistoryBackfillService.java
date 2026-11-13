package kr.co.marketry.domain.stock.service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;
import kr.co.marketry.common.exception.BadRequestException;
import kr.co.marketry.common.exception.ErrorCode;
import kr.co.marketry.domain.stock.client.YahooFinanceQuoteClient;
import kr.co.marketry.domain.stock.client.YahooFinanceQuoteClient.DailyBar;
import kr.co.marketry.domain.stock.client.YahooFinanceQuoteClient.IndexSession;
import kr.co.marketry.domain.stock.client.YahooFinanceQuoteClient.MarketValue;
import kr.co.marketry.domain.stock.collector.UsaConstituents;
import kr.co.marketry.domain.stock.collector.UsaConstituents.Constituent;
import kr.co.marketry.domain.stock.entity.HistoryBackfillProgress;
import kr.co.marketry.domain.stock.entity.MarketIndexDaily;
import kr.co.marketry.domain.stock.entity.OverseasStockQuote;
import kr.co.marketry.domain.stock.entity.OverseasStockQuoteId;
import kr.co.marketry.domain.stock.repository.HistoryBackfillProgressRepository;
import kr.co.marketry.domain.stock.repository.MarketIndexDailyRepository;
import kr.co.marketry.domain.stock.repository.OverseasStockQuoteRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

/**
 * 미국 종목의 과거 종가를 야후에서 받는다. 관리자가 시작할 때만 돈다.
 *
 * <p>과거 시가총액은 날짜별로 없어, 야후의 최신 시가총액을 주가 비율로 환산한다. 종가가 아직 확정되지 않은 날짜는 받지 않는다. 마지막
 * 종가를 저장하는 일은 수집기가 맡는다.
 */
@Slf4j
@Service
public class UsaHistoryBackfillService {
    static final String MARKET = "USA";
    static final String INDEX_MARKET = "SP500";
    private static final String INDEX_SYMBOL = "^GSPC";
    private static final String JOB = "USA";
    private static final LocalTime CLOSE_TIME = LocalTime.of(16, 0);
    private static final Duration CALL_INTERVAL = Duration.ofSeconds(1);
    private static final Duration CLOSE_GRACE = Duration.ofMinutes(30);
    private static final int LOOKBACK_DAYS = 14;
    private static final int MAX_CONSECUTIVE_FAILURES = 20;
    private static final int PRICE_SCALE = 2;
    private static final BigDecimal PERCENT = BigDecimal.valueOf(100);

    private final YahooFinanceQuoteClient client;
    private final OverseasStockQuoteRepository quotes;
    private final MarketIndexDailyRepository indexes;
    private final HistoryBackfillProgressRepository progress;
    private final UsaConstituents constituents;
    private final Clock clock;
    private final AtomicReference<Status> status =
            new AtomicReference<>(new Status(false, null, null, 0, 0, 0, 0, 0, null, null));

    @Autowired
    public UsaHistoryBackfillService(
            YahooFinanceQuoteClient client,
            OverseasStockQuoteRepository quotes,
            MarketIndexDailyRepository indexes,
            HistoryBackfillProgressRepository progress,
            UsaConstituents constituents) {
        this(client, quotes, indexes, progress, constituents, Clock.system(ZoneId.of("Asia/Seoul")));
    }

    UsaHistoryBackfillService(
            YahooFinanceQuoteClient client,
            OverseasStockQuoteRepository quotes,
            MarketIndexDailyRepository indexes,
            HistoryBackfillProgressRepository progress,
            UsaConstituents constituents,
            Clock clock) {
        this.client = client;
        this.quotes = quotes;
        this.indexes = indexes;
        this.progress = progress;
        this.constituents = constituents;
        this.clock = clock;
    }

    public Status status() {
        return status.get();
    }

    public Status start(LocalDate from, LocalDate to) {
        if (from.isAfter(to)) {
            throw new BadRequestException(ErrorCode.INVALID_INPUT, "시작일이 종료일보다 늦습니다");
        }
        LocalDate effectiveTo = completedDayAtOrBefore(to);
        if (from.isAfter(effectiveTo)) {
            throw new BadRequestException(ErrorCode.INVALID_INPUT, "받을 수 있는 종가가 시작일보다 앞입니다");
        }
        Status starting = new Status(true, from, effectiveTo, 0, 0, 0, 0, 0, null, null);
        Status previous = status.getAndUpdate(current -> current.running() ? current : starting);
        if (previous.running()) {
            return previous;
        }
        Thread worker = new Thread(() -> run(from, effectiveTo), "usa-history-backfill");
        worker.setDaemon(true);
        worker.start();
        return starting;
    }

    /** 종가가 확정된 가장 최근 날짜 이하로 종료일을 줄인다. 장이 끝나기 전의 오늘 봉은 값이 아직 움직여서 받지 않는다. */
    LocalDate completedDayAtOrBefore(LocalDate to) {
        IndexSession session = client.fetchIndexSession(INDEX_SYMBOL);
        LocalDate latest = session.latestBarDate();
        if (Instant.now(clock).isBefore(session.regularEnd().plus(CLOSE_GRACE))) {
            latest = latest.minusDays(1);
        }
        return to.isAfter(latest) ? latest : to;
    }

    void run(LocalDate from, LocalDate to) {
        List<String> items = new ArrayList<>();
        items.add(INDEX_MARKET);
        for (Constituent constituent : constituents.all()) {
            items.add(constituent.symbol());
        }
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
                        "[미국 과거 데이터 실패] | context : {}|{}|{}",
                        item,
                        e.getClass().getSimpleName(),
                        e.getMessage());
                if (consecutiveFailures >= MAX_CONSECUTIVE_FAILURES) {
                    stopReason = "연속 실패 " + consecutiveFailures + "회로 멈춤";
                    break;
                }
            }
        }
        log.info("[미국 과거 데이터 종료] | context : {}|{}|{}|{}|{}", done, skipped, failed, savedRows, stopReason);
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

    private int backfillStock(String symbol, LocalDate from, LocalDate to) throws InterruptedException {
        List<DailyBar> bars = client.fetchDailyBars(symbol, from.minusDays(LOOKBACK_DAYS), to);
        pause(CALL_INTERVAL);
        // 야후 시가총액 기록은 종목마다 마지막 갱신일이 다르다(2025-10-02에 멈춘 종목이 많다). 일봉과 같은 시작일부터 찾아서 가장 최근
        // 기록을 놓치지 않고, 그 기록의 날짜가 받은 일봉 안에 들어와 그날 종가로 환산할 수 있게 한다.
        MarketValue marketValue = client.fetchMarketValueOf(symbol, from.minusDays(LOOKBACK_DAYS), to);
        pause(CALL_INTERVAL);
        BigDecimal baseClose = JapanHistoryBackfillService.closeOn(bars, marketValue.date());
        List<OverseasStockQuote> rows = toQuotes(symbol, bars, marketValue.value(), baseClose, from);
        quotes.saveAll(rows);
        return rows.size();
    }

    /** 시작일 앞의 값은 전일 종가로만 쓴다. 전일 종가가 없는 첫날(상장일)은 등락률을 알 수 없어 저장하지 않는다. */
    static List<OverseasStockQuote> toQuotes(
            String symbol, List<DailyBar> bars, BigDecimal latestValue, BigDecimal baseClose, LocalDate from) {
        List<OverseasStockQuote> rows = new ArrayList<>();
        for (int index = 1; index < bars.size(); index++) {
            DailyBar bar = bars.get(index);
            if (bar.date().isBefore(from)) {
                continue;
            }
            BigDecimal close = price(bar.close());
            BigDecimal previous = price(bars.get(index - 1).close());
            rows.add(OverseasStockQuote.create(
                    new OverseasStockQuoteId(MARKET, symbol, bar.date().atTime(CLOSE_TIME)),
                    close,
                    previous,
                    close.subtract(previous).multiply(PERCENT).divide(previous, PRICE_SCALE, RoundingMode.HALF_UP),
                    JapanHistoryBackfillService.marketValueOf(latestValue, baseClose, close)));
        }
        return rows;
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
