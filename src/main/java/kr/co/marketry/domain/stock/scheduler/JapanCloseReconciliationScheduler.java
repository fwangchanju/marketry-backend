package kr.co.marketry.domain.stock.scheduler;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;
import kr.co.marketry.common.event.DailyPriceCollectionFailedEvent;
import kr.co.marketry.common.util.KstClock;
import kr.co.marketry.domain.stock.collector.JapanCloseReconciler;
import kr.co.marketry.domain.stock.collector.JapanMarketReference;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.annotation.Profile;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * 장 마감 뒤 저녁에 일본 종가를 야후 일봉으로 확인해 지도의 기준을 보정한다.
 * 20:00~23:50에 10분마다 시도하고, 마지막 시도까지 실패하면 한 번 알린다. 측정 결과에 따라 시간대를 다시 정한다.
 */
@Slf4j
@Component
@Profile("prod")
@RequiredArgsConstructor
public class JapanCloseReconciliationScheduler {
    private final JapanCloseReconciler reconciler;
    private final ApplicationEventPublisher events;

    private static final LocalTime FINAL_ATTEMPT_TIME = LocalTime.of(23, 50);

    @Scheduled(cron = "${japan-close-reconcile.cron:0 0/10 20-23 * * MON-FRI}", zone = "Asia/Seoul")
    public void reconcileEvening() {
        reconcileEvening(KstClock.now());
    }

    void reconcileEvening(LocalDateTime now) {
        boolean finalAttempt = now.toLocalTime().isBefore(FINAL_ATTEMPT_TIME) == false;
        reconcile(now.toLocalDate(), finalAttempt);
    }

    void reconcile(LocalDate date, boolean finalAttempt) {
        if (JapanMarketReference.isKnownHoliday(date)) {
            log.info("[일본 종가 확인] 휴장일이므로 건너뜀 날짜={}", date);
            return;
        }
        try {
            var result = reconciler.reconcile(date);
            if (result.failures().isEmpty()) {
                log.info("[일본 종가 확인 완료] 날짜={} 종목={}", date, result.saved());
                return;
            }
            if (finalAttempt == false) {
                log.info("[일본 종가 확인 대기] 날짜={} 사유={} 다음 시도에 다시 확인", date, result.failures());
                return;
            }
            log.warn(
                    "[일본 종가 확인 미완료] 날짜={} 보정={} 실패={}",
                    date,
                    result.saved(),
                    result.failures().size());
            events.publishEvent(new DailyPriceCollectionFailedEvent(date, "일본 종가 확인 미완료", result.failures()));
        } catch (RuntimeException failure) {
            log.error("[일본 종가 확인 실패] 날짜={} 오류={}", date, failure.getClass().getSimpleName());
            if (finalAttempt) {
                events.publishEvent(new DailyPriceCollectionFailedEvent(date, "일본 종가 확인 실행 실패", List.of()));
            }
        }
    }
}
