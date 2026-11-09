package kr.co.marketry.domain.stock.scheduler;

import java.time.LocalDate;
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

/** 마감 뒤 야후 일봉을 다시 확인해 다음 날 지도의 기준을 보정한다. */
@Slf4j
@Component
@Profile("prod")
@RequiredArgsConstructor
public class JapanCloseReconciliationScheduler {
    private final JapanCloseReconciler reconciler;
    private final ApplicationEventPublisher events;

    @Scheduled(cron = "${japan-close-reconcile.cron:0 0 18 * * MON-FRI}", zone = "Asia/Seoul")
    public void reconcile() {
        reconcile(KstClock.now().toLocalDate());
    }

    void reconcile(LocalDate date) {
        if (JapanMarketReference.isKnownHoliday(date)) {
            log.info("[일본 종가 확인] 휴장일이므로 건너뜀 날짜={}", date);
            return;
        }
        try {
            var result = reconciler.reconcile(date);
            if (!result.failures().isEmpty()) {
                log.warn(
                        "[일본 종가 확인 미완료] 날짜={} 보정={} 실패={}",
                        date,
                        result.saved(),
                        result.failures().size());
                events.publishEvent(new DailyPriceCollectionFailedEvent(date, "일본 종가 확인 미완료", result.failures()));
                return;
            }
            log.info("[일본 종가 확인 완료] 날짜={} 종목={}", date, result.saved());
        } catch (RuntimeException failure) {
            log.error("[일본 종가 확인 실패] 날짜={} 오류={}", date, failure.getClass().getSimpleName());
            events.publishEvent(new DailyPriceCollectionFailedEvent(date, "일본 종가 확인 실행 실패", List.of()));
        }
    }
}
