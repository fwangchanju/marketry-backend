package kr.co.marketry.domain.stock.service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Clock;
import java.time.DayOfWeek;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;
import kr.co.marketry.common.exception.BadRequestException;
import kr.co.marketry.common.exception.ErrorCode;
import kr.co.marketry.domain.stock.client.KiwoomApiClient;
import kr.co.marketry.domain.stock.client.StockDailyPriceClient;
import kr.co.marketry.domain.stock.entity.HistoryBackfillProgress;
import kr.co.marketry.domain.stock.entity.MarketIndexDaily;
import kr.co.marketry.domain.stock.entity.StockInfo;
import kr.co.marketry.domain.stock.enums.ExchangeType;
import kr.co.marketry.domain.stock.repository.HistoryBackfillProgressRepository;
import kr.co.marketry.domain.stock.repository.MarketIndexDailyRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * 한국 지수·종목 일봉의 과거 값을 키움에서 나눠 받는다. 관리자가 시작할 때만 돌고, 호출 사이를 1초 띄우며 장중과 새벽 수집 시간에는 쉰다.
 * 끝난 항목은 기록해 두어 서버가 재시작돼도 다시 시작하면 이어서 받는다.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class HistoryBackfillService {
    public static final String KOREA_INDEX = "KOREA_INDEX";
    public static final String KOREA_STOCK = "KOREA_STOCK";

    private static final Map<String, String> INDEX_CODES = Map.of("KOSPI", "001", "KOSDAQ", "101");
    private static final Duration CALL_INTERVAL = Duration.ofSeconds(1);
    private static final Duration QUIET_RECHECK = Duration.ofMinutes(5);
    private static final int MAX_CALLS_PER_ITEM = 40;
    private static final int MAX_CONSECUTIVE_FAILURES = 20;
    private static final BigDecimal INDEX_SCALE = BigDecimal.valueOf(100);

    private final KiwoomApiClient apiClient;
    private final StockDailyPriceClient stockClient;
    private final StockDailyPriceService stockPrices;
    private final StockInfoCacheService stockInfos;
    private final MarketIndexDailyRepository indexRepository;
    private final HistoryBackfillProgressRepository progressRepository;
    private final Clock clock = Clock.system(java.time.ZoneId.of("Asia/Seoul"));

    private final AtomicReference<Status> status = new AtomicReference<>(Status.idle());

    /** 장중(평일 07:30~20:30)과 백업·일봉 수집 시간(매일 04:20~06:30)에는 키움을 부르지 않는다. */
    static boolean isQuietTime(LocalDateTime now) {
        LocalTime time = now.toLocalTime();
        boolean weekday = now.getDayOfWeek() != DayOfWeek.SATURDAY && now.getDayOfWeek() != DayOfWeek.SUNDAY;
        boolean market = weekday && time.isBefore(LocalTime.of(7, 30)) == false && time.isBefore(LocalTime.of(20, 30));
        boolean nightly = time.isBefore(LocalTime.of(4, 20)) == false && time.isBefore(LocalTime.of(6, 30));
        return market == false && nightly == false;
    }

    public Status status() {
        return status.get();
    }

    public Status start(String job, LocalDate from, LocalDate to) {
        if (KOREA_INDEX.equals(job) == false && KOREA_STOCK.equals(job) == false) {
            throw new BadRequestException(ErrorCode.INVALID_INPUT, "알 수 없는 작업: " + job);
        }
        if (from.isAfter(to)) {
            throw new BadRequestException(ErrorCode.INVALID_INPUT, "시작일이 종료일보다 늦습니다");
        }
        Status starting = Status.starting(job, from, to);
        Status previous = status.getAndUpdate(current -> current.running() ? current : starting);
        if (previous.running()) {
            return previous;
        }
        Thread worker = new Thread(() -> run(job, from, to), "history-backfill");
        worker.setDaemon(true);
        worker.start();
        return starting;
    }

    void run(String job, LocalDate from, LocalDate to) {
        List<String> items = itemsOf(job);
        int done = 0;
        int skipped = 0;
        int failed = 0;
        int consecutiveFailures = 0;
        long savedRows = 0;
        String stopReason = null;
        for (String item : items) {
            if (progressRepository.existsById(new HistoryBackfillProgress.Id(progressKey(job, from), item))) {
                skipped++;
                continue;
            }
            status.set(new Status(job, true, from, to, items.size(), done, skipped, failed, savedRows, item, null));
            try {
                savedRows += backfillItem(job, item, from, to);
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
                        "[과거 데이터 받기 실패] | context : {}|{}|{}",
                        job,
                        item,
                        e.getClass().getSimpleName());
                if (consecutiveFailures >= MAX_CONSECUTIVE_FAILURES) {
                    stopReason = "연속 실패 " + consecutiveFailures + "회로 멈춤";
                    break;
                }
            }
        }
        log.info("[과거 데이터 받기 종료] | context : {}|{}|{}|{}|{}|{}", job, done, skipped, failed, savedRows, stopReason);
        status.set(new Status(job, false, from, to, items.size(), done, skipped, failed, savedRows, null, stopReason));
    }

    // 시작일마다 따로 기록한다. 짧은 기간을 먼저 시험해도 전체 받기에서 그 항목을 건너뛰지 않는다.
    private static String progressKey(String job, LocalDate from) {
        return job + ":" + from;
    }

    private List<String> itemsOf(String job) {
        if (KOREA_INDEX.equals(job)) {
            return INDEX_CODES.keySet().stream().sorted().toList();
        }
        return stockInfos.getCache().values().stream()
                .filter(StockInfo::isActiveAndOrdinary)
                .map(StockInfo::getStockCode)
                .sorted()
                .toList();
    }

    private long backfillItem(String job, String item, LocalDate from, LocalDate to) throws InterruptedException {
        LocalDate base = to;
        LocalDate oldest = null;
        long saved = 0;
        for (int call = 0; call < MAX_CALLS_PER_ITEM && base.isBefore(from) == false; call++) {
            waitForQuietTime();
            List<Map<String, Object>> rows = KOREA_INDEX.equals(job)
                    ? fetchIndex(item, base)
                    : stockClient.fetchFirstPage(item, ExchangeType.KRX, base);
            pause(CALL_INTERVAL);
            Optional<LocalDate> pageOldest = rows.stream()
                    .map(row -> String.valueOf(row.get("dt")))
                    .filter(date -> date.isBlank() == false && "null".equals(date) == false)
                    .map(date -> LocalDate.parse(date, DateTimeFormatter.BASIC_ISO_DATE))
                    .min(LocalDate::compareTo);
            if (pageOldest.isEmpty() || pageOldest.get().isAfter(base)) {
                break;
            }
            List<Map<String, Object>> inRange =
                    rows.stream().filter(row -> withinRange(row, from, to)).toList();
            saved += KOREA_INDEX.equals(job) ? saveIndex(item, inRange) : saveStock(item, inRange);
            oldest = pageOldest.get().isBefore(from) ? from : pageOldest.get();
            base = pageOldest.get().minusDays(1);
        }
        progressRepository.save(HistoryBackfillProgress.create(
                progressKey(job, from), item, (int) saved, oldest, LocalDateTime.now(clock)));
        return saved;
    }

    private boolean withinRange(Map<String, Object> row, LocalDate from, LocalDate to) {
        String value = String.valueOf(row.get("dt"));
        if (value.isBlank() || "null".equals(value)) {
            return false;
        }
        LocalDate date = LocalDate.parse(value, DateTimeFormatter.BASIC_ISO_DATE);
        return date.isBefore(from) == false && date.isAfter(to) == false;
    }

    private long saveStock(String stockCode, List<Map<String, Object>> rows) {
        var result = stockPrices.saveHistoryPage(
                stockClient.parsePrices(stockCode, ExchangeType.KRX, rows, LocalDateTime.now(clock)));
        return result.inserted() + (long) result.skipped();
    }

    private long saveIndex(String market, List<Map<String, Object>> rows) {
        List<MarketIndexDaily> values = new ArrayList<>();
        for (Map<String, Object> row : rows) {
            values.add(parseIndexRow(market, row, LocalDateTime.now(clock)));
        }
        indexRepository.saveAll(values);
        return values.size();
    }

    /** ka20006의 지수 값은 소수점을 뺀 100배 정수로 온다(505969 → 5059.69). */
    static MarketIndexDaily parseIndexRow(String market, Map<String, Object> row, LocalDateTime collectedAt) {
        return MarketIndexDaily.create(
                market,
                LocalDate.parse(String.valueOf(row.get("dt")), DateTimeFormatter.BASIC_ISO_DATE),
                indexValue(row, "open_pric"),
                indexValue(row, "high_pric"),
                indexValue(row, "low_pric"),
                indexValue(row, "cur_prc"),
                Long.parseLong(digits(row, "trde_qty")),
                Long.valueOf(digits(row, "trde_prica")),
                collectedAt);
    }

    private static BigDecimal indexValue(Map<String, Object> row, String field) {
        return new BigDecimal(digits(row, field)).divide(INDEX_SCALE, 2, RoundingMode.UNNECESSARY);
    }

    private static String digits(Map<String, Object> row, String field) {
        return String.valueOf(row.get(field)).replace(",", "").replace("+", "").replace("-", "");
    }

    @SuppressWarnings("unchecked")
    private List<Map<String, Object>> fetchIndex(String market, LocalDate base) {
        Map<String, Object> body = apiClient.postFirstPage(
                "ka20006",
                "/api/dostk/chart",
                Map.of("inds_cd", INDEX_CODES.get(market), "base_dt", base.format(DateTimeFormatter.BASIC_ISO_DATE)));
        List<Map<String, Object>> rows = new ArrayList<>();
        if (body.get("inds_dt_pole_qry") instanceof List<?> list) {
            for (Object row : list) {
                if (row instanceof Map<?, ?>) {
                    rows.add((Map<String, Object>) row);
                }
            }
        }
        return rows;
    }

    private void waitForQuietTime() throws InterruptedException {
        while (isQuietTime(LocalDateTime.now(clock)) == false) {
            pause(QUIET_RECHECK);
        }
    }

    void pause(Duration duration) throws InterruptedException {
        Thread.sleep(duration);
    }

    public record Status(
            String job,
            boolean running,
            LocalDate from,
            LocalDate to,
            int total,
            int done,
            int skipped,
            int failed,
            long savedRows,
            String currentItem,
            String stopReason) {
        static Status idle() {
            return new Status(null, false, null, null, 0, 0, 0, 0, 0, null, null);
        }

        static Status starting(String job, LocalDate from, LocalDate to) {
            return new Status(job, true, from, to, 0, 0, 0, 0, 0, null, null);
        }
    }
}
