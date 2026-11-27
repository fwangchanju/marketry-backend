package kr.co.marketry.domain.stock.scheduler;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;
import kr.co.marketry.common.event.DailyPriceCollectionFailedEvent;
import kr.co.marketry.domain.stock.collector.UsaCloseReconciler;
import kr.co.marketry.domain.stock.collector.UsaMarketSession;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.annotation.Profile;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * 미국 장이 끝난 뒤(뉴욕 20:00~23:50, 10분마다) 그날 종가를 야후 일봉으로 확인해 종가 줄을 확정 종가로 보정하고 빠진 종목을 채운다.
 * 마지막 시도까지 못 채운 종목이 있으면 한 번 알린다. 휴장일처럼 종가 줄이 없는 날은 조용히 건너뛴다. 일본 종가 확인과 같은 방식이다.
 */
@Slf4j
@Component
@Profile("prod")
@RequiredArgsConstructor
public class UsaCloseReconciliationScheduler {
    private static final LocalTime FINAL_ATTEMPT_TIME = LocalTime.of(23, 50);

    private final UsaCloseReconciler reconciler;
    private final ApplicationEventPublisher events;

    @Scheduled(cron = "${usa-close-reconcile.cron:0 0/10 20-23 * * MON-FRI}", zone = "America/New_York")
    public void reconcileEvening() {
        reconcileEvening(LocalDateTime.now(UsaMarketSession.NEW_YORK));
    }

    void reconcileEvening(LocalDateTime now) {
        boolean finalAttempt = now.toLocalTime().isBefore(FINAL_ATTEMPT_TIME) == false;
        reconcile(now.toLocalDate(), finalAttempt);
    }

    void reconcile(LocalDate date, boolean finalAttempt) {
        try {
            var result = reconciler.reconcile(date);
            if (result.failures().isEmpty()) {
                log.info("[미국 종가 확인 완료] 날짜={} 보정={}", date, result.saved());
                return;
            }
            if (finalAttempt == false) {
                log.info(
                        "[미국 종가 확인 대기] 날짜={} 남은 종목={} 다음 시도에 다시 확인",
                        date,
                        result.failures().size());
                return;
            }
            log.warn(
                    "[미국 종가 확인 미완료] 날짜={} 보정={} 실패={}",
                    date,
                    result.saved(),
                    result.failures().size());
            events.publishEvent(new DailyPriceCollectionFailedEvent(date, "미국 종가 확인 미완료", result.failures()));
        } catch (RuntimeException failure) {
            log.error("[미국 종가 확인 실패] 날짜={} 오류={}", date, failure.getClass().getSimpleName());
            if (finalAttempt) {
                events.publishEvent(new DailyPriceCollectionFailedEvent(date, "미국 종가 확인 실행 실패", List.of()));
            }
        }
    }
}
