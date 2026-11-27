package kr.co.marketry.domain.stock.scheduler;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import kr.co.marketry.domain.stock.collector.UsaCloseReconciler;
import org.junit.jupiter.api.Test;
import org.springframework.context.ApplicationEventPublisher;

class UsaCloseReconciliationSchedulerTest {
    @Test
    void 뉴욕_저녁_여덟_시에는_그날_종가를_확인한다() {
        var reconciler = mock(UsaCloseReconciler.class);
        var scheduler = new UsaCloseReconciliationScheduler(reconciler, mock(ApplicationEventPublisher.class));

        scheduler.reconcileEvening(LocalDateTime.of(2026, 11, 27, 20, 0));

        verify(reconciler).reconcile(LocalDate.of(2026, 11, 27));
    }

    @Test
    void 마지막_시도_전에_남은_종목이_있어도_알리지_않고_다음_시도를_기다린다() {
        var reconciler = mock(UsaCloseReconciler.class);
        var events = mock(ApplicationEventPublisher.class);
        when(reconciler.reconcile(any())).thenReturn(new UsaCloseReconciler.Result(499, List.of("HUBB")));
        var scheduler = new UsaCloseReconciliationScheduler(reconciler, events);

        scheduler.reconcileEvening(LocalDateTime.of(2026, 11, 27, 23, 40));

        verify(events, never()).publishEvent((Object) any());
    }

    @Test
    void 마지막_시도에도_남은_종목이_있으면_한_번_알린다() {
        var reconciler = mock(UsaCloseReconciler.class);
        var events = mock(ApplicationEventPublisher.class);
        when(reconciler.reconcile(any())).thenReturn(new UsaCloseReconciler.Result(499, List.of("HUBB")));
        var scheduler = new UsaCloseReconciliationScheduler(reconciler, events);

        scheduler.reconcileEvening(LocalDateTime.of(2026, 11, 27, 23, 50));

        verify(events, times(1)).publishEvent((Object) any());
    }

    @Test
    void 종가_줄이_없는_휴장일처럼_할_일이_없으면_알리지_않는다() {
        var reconciler = mock(UsaCloseReconciler.class);
        var events = mock(ApplicationEventPublisher.class);
        when(reconciler.reconcile(any())).thenReturn(new UsaCloseReconciler.Result(0, List.of()));
        var scheduler = new UsaCloseReconciliationScheduler(reconciler, events);

        scheduler.reconcileEvening(LocalDateTime.of(2026, 11, 26, 23, 50));

        verify(events, never()).publishEvent((Object) any());
    }
}
