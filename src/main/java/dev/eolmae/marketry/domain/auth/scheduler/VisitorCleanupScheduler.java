package dev.eolmae.marketry.domain.auth.scheduler;

import dev.eolmae.marketry.common.exception.ErrorCode;
import dev.eolmae.marketry.common.exception.EscalateException;
import dev.eolmae.marketry.domain.auth.service.VisitorService;
import dev.eolmae.marketry.domain.notification.listener.EscalationPublisher;
import lombok.RequiredArgsConstructor;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/** 오래된 비회원 방문 기록 정리 배치. 삭제 로직은 {@link VisitorService}에 두고, 여기서는 언제 돌지만 담당한다. */
@Component
@RequiredArgsConstructor
public class VisitorCleanupScheduler {

    private static final String KST_ZONE_ID = "Asia/Seoul";
    // 대시보드가 보여 주는 최대 기간(90일)보다 넉넉하게 둔다.
    static final int RETENTION_DAYS = 180;

    private final VisitorService visitorService;
    private final EscalationPublisher escalationPublisher;

    @Scheduled(cron = "0 15 4 * * *", zone = KST_ZONE_ID)
    public void cleanupVisitors() {
        try {
            visitorService.purgeOlderThan(RETENTION_DAYS);
        } catch (Exception e) {
            escalationPublisher.report(EscalateException.wrap(ErrorCode.VISITOR_CLEANUP_FAILED, e));
        }
    }
}
