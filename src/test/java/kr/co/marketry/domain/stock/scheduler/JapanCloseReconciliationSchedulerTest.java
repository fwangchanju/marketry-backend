package kr.co.marketry.domain.stock.scheduler;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import java.time.LocalDate;
import kr.co.marketry.domain.stock.collector.JapanCloseReconciler;
import org.junit.jupiter.api.Test;
import org.springframework.context.ApplicationEventPublisher;

class JapanCloseReconciliationSchedulerTest {
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
