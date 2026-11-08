package kr.co.marketry.domain.stock.scheduler;

import java.time.LocalDate;
import kr.co.marketry.common.exception.ErrorCode;
import kr.co.marketry.common.exception.EscalateException;
import kr.co.marketry.common.util.KstClock;
import kr.co.marketry.domain.notification.listener.EscalationPublisher;
import kr.co.marketry.domain.stock.service.JapanQuoteRetentionService;
import kr.co.marketry.domain.stock.service.SectorPriceSnapshotService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * 최근 5거래일의 5분 시세를 보존하고, 이전 거래일은 시장별 종가 시각의 종목 행만 남긴다.
 * 날짜별 실패를 격리하며 다음 실행에서 다시 처리한다.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class SnapshotRetentionScheduler {

    // 5분 시세를 그대로 두는 최근 거래일 수. 그 앞 날짜는 종가 시점만 남긴다. 한국과 일본이 같다.
    private static final int RETAINED_TRADING_DAYS = 5;
    private static final String KST_ZONE_ID = "Asia/Seoul";

    private final SectorPriceSnapshotService sectorPriceSnapshotService;
    private final JapanQuoteRetentionService japanQuoteRetentionService;
    private final EscalationPublisher escalationPublisher;

    @Scheduled(cron = "0 0 4 * * *", zone = KST_ZONE_ID)
    public void cleanupSnapshots() {
        run("섹터가격스냅샷정리 대상 조회", () -> cleanupSnapshots(KstClock.now().toLocalDate()));
    }

    void cleanupSnapshots(LocalDate today) {
        var dates = sectorPriceSnapshotService.findCleanupDates(today, RETAINED_TRADING_DAYS);
        log.info("스냅샷 정리 배치 시작: days={}", dates.size());
        for (LocalDate date : dates) {
            run("섹터가격스냅샷정리 " + date, () -> sectorPriceSnapshotService.cleanupSnapshotsForDate(date));
        }
        run("일본시세정리", () -> japanQuoteRetentionService.cleanup(RETAINED_TRADING_DAYS));
        log.info("스냅샷 정리 배치 종료");
    }

    // CollectionScheduler.run()과 동일한 격리 패턴 — 한 테이블 정리가 실패해도 다른 테이블 정리는 계속한다.
    private void run(String taskName, Runnable task) {
        try {
            task.run();
            escalationPublisher.resolve(taskName);
        } catch (Exception e) {
            escalationPublisher.report(
                    taskName, EscalateException.wrap(ErrorCode.SNAPSHOT_RETENTION_FAILED, e, taskName));
        }
    }
}
