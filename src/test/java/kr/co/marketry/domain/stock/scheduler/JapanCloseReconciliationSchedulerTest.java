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
import kr.co.marketry.domain.stock.collector.JapanCloseReconciler;
import org.junit.jupiter.api.Test;
import org.springframework.context.ApplicationEventPublisher;

class JapanCloseReconciliationSchedulerTest {
    @Test
    void 저녁_여덟_시에는_당일_종가를_확인한다() {
        var reconciler = mock(JapanCloseReconciler.class);
        var scheduler = new JapanCloseReconciliationScheduler(reconciler, mock(ApplicationEventPublisher.class));

        scheduler.reconcileEvening(LocalDateTime.of(2026, 11, 13, 20, 0));

        verify(reconciler).reconcile(LocalDate.of(2026, 11, 13));
    }

    @Test
    void 마감_시각_전_실패는_알리지_않고_다음_시도를_기다린다() {
        var reconciler = mock(JapanCloseReconciler.class);
        var events = mock(ApplicationEventPublisher.class);
        when(reconciler.reconcile(any())).thenReturn(new JapanCloseReconciler.Result(0, List.of("닛케이 일봉 없음")));
        var scheduler = new JapanCloseReconciliationScheduler(reconciler, events);

        scheduler.reconcileEvening(LocalDateTime.of(2026, 11, 13, 23, 40));

        verify(events, never()).publishEvent((Object) any());
    }

    @Test
    void 마감_시각에도_실패하면_한_번_알린다() {
        var reconciler = mock(JapanCloseReconciler.class);
        var events = mock(ApplicationEventPublisher.class);
        when(reconciler.reconcile(any())).thenReturn(new JapanCloseReconciler.Result(0, List.of("닛케이 일봉 없음")));
        var scheduler = new JapanCloseReconciliationScheduler(reconciler, events);

        scheduler.reconcileEvening(LocalDateTime.of(2026, 11, 13, 23, 50));

        verify(events, times(1)).publishEvent((Object) any());
    }

    @Test
    void 확인에_성공하면_알리지_않는다() {
        var reconciler = mock(JapanCloseReconciler.class);
        var events = mock(ApplicationEventPublisher.class);
        when(reconciler.reconcile(any())).thenReturn(new JapanCloseReconciler.Result(225, List.of()));
        var scheduler = new JapanCloseReconciliationScheduler(reconciler, events);

        scheduler.reconcileEvening(LocalDateTime.of(2026, 11, 13, 23, 50));

        verify(events, never()).publishEvent((Object) any());
    }

    @Test
    void 일본_휴장일_저녁에는_조회와_알림을_모두_건너뛴다() {
        var reconciler = mock(JapanCloseReconciler.class);
        var events = mock(ApplicationEventPublisher.class);
        var scheduler = new JapanCloseReconciliationScheduler(reconciler, events);

        scheduler.reconcileEvening(LocalDateTime.of(2026, 11, 23, 23, 50));

        verify(reconciler, never()).reconcile(any());
        verify(events, never()).publishEvent((Object) any());
    }
}
