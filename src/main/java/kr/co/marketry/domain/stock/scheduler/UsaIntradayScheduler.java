package kr.co.marketry.domain.stock.scheduler;

import kr.co.marketry.common.exception.ErrorCode;
import kr.co.marketry.common.exception.EscalateException;
import kr.co.marketry.domain.notification.listener.EscalationPublisher;
import kr.co.marketry.domain.stock.collector.UsaIntradayCollector;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.scheduling.annotation.Schedules;
import org.springframework.stereotype.Component;

/**
 * 미국 장중 시세 수집(프리마켓·정규장·애프터마켓): 월 17:02부터 토 09:57까지(한국 시각) 5분 간격. 프리마켓은 한국 시각으로 겨울 18:00, 서머타임 17:00에
 * 시작하고 애프터마켓은 겨울 10:00, 서머타임 09:00에 끝나서 넉넉히 부르고, 실제로 장 시간인지는 야후가 알려 주는 그날 세션 시각으로 수집기가
 * 판단한다. 종가 수집(UsaQuoteScheduler)과 묶지 않는다. 야후 5분 칸이 채워지도록 정각보다 2분 늦게 부른다.
 */
@Slf4j
@Component
public class UsaIntradayScheduler {
    private static final int ALERT_AFTER_FAILURES = 3;
    private static final int REALERT_EVERY_FAILURES = 12;
    private static final String ALERT_SOURCE = "미국";

    private final UsaIntradayCollector collector;
    private final EscalationPublisher escalationPublisher;
    private final boolean collectEnabled;
    private int consecutiveFailures;

    public UsaIntradayScheduler(
            UsaIntradayCollector collector,
            EscalationPublisher escalationPublisher,
            @Value("${usa-intraday.enabled:false}") boolean collectEnabled) {
        this.collector = collector;
        this.escalationPublisher = escalationPublisher;
        this.collectEnabled = collectEnabled;
    }

    @Schedules({
        @Scheduled(cron = "${usa-intraday.evening-cron:0 2/5 17-23 * * MON-FRI}", zone = "Asia/Seoul"),
        @Scheduled(cron = "${usa-intraday.morning-cron:0 2/5 0-9 * * TUE-SAT}", zone = "Asia/Seoul")
    })
    public synchronized void collect() {
        if (collectEnabled == false) {
            return;
        }
        try {
            UsaIntradayCollector.Result result = collector.collect();
            log.info(
                    "[미국장중] {}: snapshotTime={}, 저장={}/{}, {}",
                    result.saved() ? "저장" : "저장 안 함",
                    result.snapshotTime(),
                    result.savedCount(),
                    result.totalCount(),
                    result.reason() == null ? "" : result.reason());
            if (result.fetchFailed()) {
                recordFailure(result.reason());
            } else {
                consecutiveFailures = 0;
            }
        } catch (RuntimeException failure) {
            log.warn("[미국장중] 수집 실패", failure);
            recordFailure(failure.getClass().getSimpleName() + ": " + failure.getMessage());
        }
    }

    // 연속으로 실패한 회차만 센다. 성공하거나 장 시간 밖·새 시세 없음 같은 정상 회차가 끼면 다시 센다(미국 종가 수집과 같은 규칙이고, 5분 간격이라
    // 재알림 12회는 약 1시간이다). 알림 실패는 수집을 막지 않는다.
    private void recordFailure(String reason) {
        consecutiveFailures++;
        boolean firstAlert = consecutiveFailures == ALERT_AFTER_FAILURES;
        boolean repeatAlert = consecutiveFailures > ALERT_AFTER_FAILURES
                && (consecutiveFailures - ALERT_AFTER_FAILURES) % REALERT_EVERY_FAILURES == 0;
        if (firstAlert == false && repeatAlert == false) {
            return;
        }
        try {
            escalationPublisher.report(
                    ALERT_SOURCE,
                    new EscalateException(
                            ErrorCode.USA_INTRADAY_COLLECT_FAILED,
                            new IllegalStateException("연속 " + consecutiveFailures + "회 실패: " + reason)));
        } catch (RuntimeException alertFailure) {
            log.warn("[미국장중] 알림 발송 실패", alertFailure);
        }
    }
}
