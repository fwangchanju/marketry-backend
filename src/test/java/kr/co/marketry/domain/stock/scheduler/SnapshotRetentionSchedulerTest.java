package kr.co.marketry.domain.stock.scheduler;

import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.LocalDate;
import java.util.List;
import kr.co.marketry.domain.notification.listener.EscalationPublisher;
import kr.co.marketry.domain.stock.service.SectorPriceSnapshotService;
import org.junit.jupiter.api.Test;

class SnapshotRetentionSchedulerTest {
    @Test
    void 한_날짜가_실패해도_다른_오래된_날짜는_계속_정리한다() {
        var service = mock(SectorPriceSnapshotService.class);
        var publisher = mock(EscalationPublisher.class);
        var japan = mock(kr.co.marketry.domain.stock.service.JapanQuoteRetentionService.class);
        LocalDate today = LocalDate.of(2026, 10, 10);
        LocalDate first = LocalDate.of(2026, 9, 1);
        LocalDate second = LocalDate.of(2026, 9, 2);
        when(service.findCleanupDates(today, 5)).thenReturn(List.of(first, second));
        doThrow(new RuntimeException("failure")).when(service).cleanupSnapshotsForDate(first);
        new SnapshotRetentionScheduler(service, japan, publisher).cleanupSnapshots(today);
        verify(japan).cleanup(5);
        verify(service).cleanupSnapshotsForDate(first);
        verify(service).cleanupSnapshotsForDate(second);
        verify(publisher).report(org.mockito.ArgumentMatchers.anyString(), org.mockito.ArgumentMatchers.any());
    }
}
