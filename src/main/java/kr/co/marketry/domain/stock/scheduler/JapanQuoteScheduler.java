package kr.co.marketry.domain.stock.scheduler;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import kr.co.marketry.common.exception.ErrorCode;
import kr.co.marketry.common.exception.EscalateException;
import kr.co.marketry.common.util.KstClock;
import kr.co.marketry.domain.notification.listener.EscalationPublisher;
import kr.co.marketry.domain.stock.collector.JapanQuoteCollector;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * 일본 시세 수집: 평일 09:18~15:58, 5분 간격. 묶지 않아서 야후가 느리거나 막혀도
 * 한국 수집과 텔레그램 발송에 영향이 없다. 점심 재개 시세의 지연을 고려해 11:58부터 12:48 전까지 조회를 건너뛴다.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class JapanQuoteScheduler {
    private static final LocalTime FIRST_COLLECTION_TIME = LocalTime.of(9, 18);
    // 판단 전제: Yahoo 시세가 약 15분 지연되고 정규 수집은 매시 03, 08, ... 53, 58분에 시작한다.
    // 오전장 마지막 11:30 시세는 11:45경 제공된다고 보고 11:48과 11:53에 조회한다.
    // 오후장 12:30 시세는 12:45경 제공된다고 보고 그 다음 정규 회차인 12:48에 조회를 재개한다.
    // 2026-11-11 운영 확인: 09:18 회차가 09:00 시세 216/225, 11:48 회차가 11:30 시세 전 종목, 12:48 회차가 12:30 시세
    // 전 종목을 저장했다. 이 시각으로 확정하고 09:15 개장 측정은 없앴다. 시세 도착이 늦어지면 저장 로그의 오늘 시세 수로 본다.
    // 이 기간에 조회를 중단하면 예상보다 늦게 도착한 오전장 시세는 12:48까지 저장이 늦어질 수 있다.
    private static final LocalTime LUNCH_COLLECTION_PAUSE_START = LocalTime.of(11, 58);
    private static final LocalTime LUNCH_COLLECTION_RESUME = LocalTime.of(12, 48);
    // 연속 실패가 이 횟수(5분 간격이므로 15분)에 닿으면 개발자에게 알리고, 이어지면 12회(1시간)마다 다시 알린다.
    private static final int ALERT_AFTER_FAILURES = 3;
    private static final int REALERT_EVERY_FAILURES = 12;
    private final JapanQuoteCollector collector;
    private final EscalationPublisher escalationPublisher;
    private int consecutiveFailures;
    private LocalDate failureDate;

    @Scheduled(cron = "${japan-quote.cron:0 3/5 9-15 * * MON-FRI}", zone = "Asia/Seoul")
    public synchronized void collect() {
        collect(KstClock.now());
    }

    void collect(LocalDateTime now) {
        if (now.toLocalTime().isBefore(FIRST_COLLECTION_TIME)) {
            return;
        }
        if (now.toLocalTime().isBefore(LUNCH_COLLECTION_PAUSE_START) == false
                && now.toLocalTime().isBefore(LUNCH_COLLECTION_RESUME)) {
            return;
        }
        try {
            JapanQuoteCollector.Result result = collector.collect();
            if (result.fetchFailed()) {
                recordFailure(now, result.reason());
            } else {
                consecutiveFailures = 0;
            }
            if (result.saved()) {
                log.info(
                        "[일본시세] 저장: snapshotTime={}, 오늘 시세={}/{}, 오늘 외={}, 조회 실패={}, 응답={}/{}",
                        result.snapshotTime(),
                        result.todayQuoteCount(),
                        result.totalCount(),
                        result.otherDateQuoteCount(),
                        result.failureCount(),
                        result.successCount(),
                        result.totalCount());
                return;
            }
            log.info(
                    "[일본시세] 저장 안 함: {}, 오늘 시세={}/{}, 오늘 외={}, 조회 실패={}, 응답={}/{}",
                    result.reason(),
                    result.todayQuoteCount(),
                    result.totalCount(),
                    result.otherDateQuoteCount(),
                    result.failureCount(),
                    result.successCount(),
                    result.totalCount());
        } catch (RuntimeException failure) {
            log.warn("[일본시세] 수집 실패", failure);
            recordFailure(now, failure.getClass().getSimpleName() + ": " + failure.getMessage());
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
        try {
            escalationPublisher.report(
                    "일본",
                    new EscalateException(
                            ErrorCode.JAPAN_QUOTE_COLLECT_FAILED,
                            new IllegalStateException("연속 " + consecutiveFailures + "회 실패: " + reason)));
        } catch (RuntimeException alertFailure) {
            log.warn("[일본시세] 수집 실패 알림 발송 실패", alertFailure);
        }
    }
}
