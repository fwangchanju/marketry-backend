package kr.co.marketry.domain.stock.scheduler;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import java.time.LocalDate;
import java.time.LocalDateTime;
import kr.co.marketry.domain.stock.collector.JapanCloseReconciler;
import org.junit.jupiter.api.Test;
import org.springframework.context.ApplicationEventPublisher;

class JapanCloseReconciliationSchedulerTest {
    @Test
    void 장_마감_전_시작이면_당일_보정을_앞당기지_않는다() {
        var reconciler = mock(JapanCloseReconciler.class);
        var scheduler = new JapanCloseReconciliationScheduler(reconciler, mock(ApplicationEventPublisher.class));

        scheduler.reconcileAfterCloseOnStartup(LocalDateTime.of(2026, 11, 9, 17, 59));

        verify(reconciler, never()).reconcile(any());
    }

    @Test
    void 장_마감_후_시작이면_오늘_보정을_따라잡는다() {
        var reconciler = mock(JapanCloseReconciler.class);
        var scheduler = new JapanCloseReconciliationScheduler(reconciler, mock(ApplicationEventPublisher.class));

        scheduler.reconcileAfterCloseOnStartup(LocalDateTime.of(2026, 11, 9, 18, 1));

        verify(reconciler).reconcile(LocalDate.of(2026, 11, 9));
    }

    @Test
    void 일본_휴장일에는_조회와_알림을_모두_건너뛴다() {
        var reconciler = mock(JapanCloseReconciler.class);
        var events = mock(ApplicationEventPublisher.class);
        var scheduler = new JapanCloseReconciliationScheduler(reconciler, events);

        scheduler.reconcile(LocalDate.of(2026, 11, 23));

        verify(reconciler, never()).reconcile(any());
        verify(events, never()).publishEvent((Object) any());
    }
}
