package kr.co.marketry.domain.stock.scheduler;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;
import kr.co.marketry.common.exception.ErrorCode;
import kr.co.marketry.common.exception.EscalateException;
import kr.co.marketry.common.util.KstClock;
import kr.co.marketry.domain.notification.listener.EscalationPublisher;
import kr.co.marketry.domain.notification.schedule.TelegramSendSchedule;
import kr.co.marketry.domain.notification.service.TelegramReportDispatcher;
import kr.co.marketry.domain.stock.collector.UsaQuoteCollector;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * 미국 시세 수집: 화~토 05:00~08:50(한국 시각), 5분 간격으로 시도하고 하루에 한 번 저장한다. 정규장 종료 + 대기 시간이 지나기
 * 전 회차는 저장하지 않는다(겨울 마감 06:00, 여름 마감 05:00). 일본 수집과 묶지 않는다.
 * 종가를 저장한 회차에서 바로 미국 지도를 텔레그램으로 한 번 보낸다(관리자 메시지 페이지의 발송 스위치가 꺼져 있으면 보내지 않는다).
 */
@Slf4j
@Component
public class UsaQuoteScheduler {
    private static final LocalTime LAST_COLLECTION_TIME = LocalTime.of(8, 50);
    // 개장(09:00) 전에 보는 용도라 07:05 회차까지 저장되지 않았으면 알린다. 07:00 정각 회차는 저장 직전일 수 있어 지난 뒤부터 본다.
    private static final LocalTime MISSING_ALERT_FROM = LocalTime.of(7, 5);
    private static final int ALERT_AFTER_FAILURES = 3;
    private static final int REALERT_EVERY_FAILURES = 12;
    private static final String ALERT_SOURCE = "미국";

    private final UsaQuoteCollector collector;
    private final EscalationPublisher escalationPublisher;
    private final TelegramReportDispatcher telegramReportDispatcher;
    private final TelegramSendSchedule telegramSendSchedule;
    private final boolean collectEnabled;
    private int consecutiveFailures;
    private LocalDate failureDate;
    private String missingAlertedKey;
    private List<LocalDate> missingIndexAlerted = List.of();

    public UsaQuoteScheduler(
            UsaQuoteCollector collector,
            EscalationPublisher escalationPublisher,
            TelegramReportDispatcher telegramReportDispatcher,
            TelegramSendSchedule telegramSendSchedule,
            @Value("${usa-quote.enabled:false}") boolean collectEnabled) {
        this.collector = collector;
        this.escalationPublisher = escalationPublisher;
        this.telegramReportDispatcher = telegramReportDispatcher;
        this.telegramSendSchedule = telegramSendSchedule;
        this.collectEnabled = collectEnabled;
    }

    @Scheduled(cron = "${usa-quote.cron:0 0/5 5-8 * * TUE-SAT}", zone = "Asia/Seoul")
    public synchronized void collect() {
        collect(KstClock.now());
    }

    void collect(LocalDateTime now) {
        if (collectEnabled == false || now.toLocalTime().isAfter(LAST_COLLECTION_TIME)) {
            return;
        }
        UsaQuoteCollector.Result result = null;
        try {
            result = collector.collect();
            if (result.fetchFailed()) {
                recordFailure(now, result.reason());
            } else if (result.saved()) {
                consecutiveFailures = 0;
            }
            log.info(
                    "[미국시세] {}: day={}, snapshotTime={}, 응답={}/{}, 조회 실패={}, {}",
                    result.saved() ? "저장" : "저장 안 함",
                    result.day(),
                    result.snapshotTime(),
                    result.successCount(),
                    result.totalCount(),
                    result.failureCount(),
                    result.reason() == null ? "" : result.reason());
        } catch (RuntimeException failure) {
            log.warn("[미국시세] 수집 실패", failure);
            recordFailure(now, failure.getClass().getSimpleName() + ": " + failure.getMessage());
        }
        alertIfMissing(now, result);
        alertIfIndexMissing(now, result);
        if (result != null && result.saved()) {
            sendUsaMap();
        }
    }

    // 발송 실패는 발송기가 알린다. 여기서는 예상하지 못한 예외가 수집 기록을 깨지 않게만 막는다.
    private void sendUsaMap() {
        try {
            if (telegramSendSchedule.sendEnabled()) {
                telegramReportDispatcher.sendUsa();
            }
        } catch (RuntimeException failure) {
            log.warn("[미국시세] 미국 지도 발송 실패", failure);
        }
    }

    // 하루 안에서 연속으로 실패한 횟수만 센다. 날이 바뀌면 다시 센다. 알림 실패는 수집을 막지 않는다.
    private void recordFailure(LocalDateTime now, String reason) {
        if (now.toLocalDate().equals(failureDate) == false) {
            failureDate = now.toLocalDate();
            consecutiveFailures = 0;
        }
        consecutiveFailures++;
        boolean firstAlert = consecutiveFailures == ALERT_AFTER_FAILURES;
        boolean repeatAlert = consecutiveFailures > ALERT_AFTER_FAILURES
                && (consecutiveFailures - ALERT_AFTER_FAILURES) % REALERT_EVERY_FAILURES == 0;
        if (firstAlert == false && repeatAlert == false) {
            return;
        }
        report(new EscalateException(
                ErrorCode.USA_QUOTE_COLLECT_FAILED,
                new IllegalStateException("연속 " + consecutiveFailures + "회 실패: " + reason)));
    }

    // 미국 거래일 하루에 한 번만 알린다. 최신 봉 날짜를 모르면(야후 호출 실패) 한국 날짜로 센다.
    // 이미 저장했다는 응답은 저장 완료이거나 휴장일이라 알리지 않는다.
    private void alertIfMissing(LocalDateTime now, UsaQuoteCollector.Result result) {
        if (now.toLocalTime().isBefore(MISSING_ALERT_FROM)) {
            return;
        }
        if (result != null && (result.saved() || result.status() == UsaQuoteCollector.Status.ALREADY_SAVED)) {
            return;
        }
        String key = result != null && result.day() != null
                ? result.day().toString()
                : now.toLocalDate().toString();
        if (key.equals(missingAlertedKey)) {
            return;
        }
        missingAlertedKey = key;
        String reason = result == null ? "야후 호출 실패" : result.reason();
        report(new EscalateException(ErrorCode.USA_QUOTE_MISSING, new IllegalStateException(reason)));
    }

    // 종목은 저장했는데 지수가 비어 있으면 화면의 지수 등락률이 틀린다. 같은 날짜들은 한 번만 알린다.
    private void alertIfIndexMissing(LocalDateTime now, UsaQuoteCollector.Result result) {
        if (now.toLocalTime().isBefore(MISSING_ALERT_FROM) || result == null) {
            return;
        }
        List<LocalDate> missing = result.missingIndexDays();
        if (missing.isEmpty() || missing.equals(missingIndexAlerted)) {
            return;
        }
        missingIndexAlerted = missing;
        report(new EscalateException(ErrorCode.USA_INDEX_MISSING, new IllegalStateException("빈 날짜: " + missing)));
    }

    private void report(EscalateException exception) {
        try {
            escalationPublisher.report(ALERT_SOURCE, exception);
        } catch (RuntimeException alertFailure) {
            log.warn("[미국시세] 알림 발송 실패", alertFailure);
        }
    }
}
