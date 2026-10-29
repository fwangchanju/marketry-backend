package dev.eolmae.marketry.domain.stock.scheduler;

import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import dev.eolmae.marketry.domain.notification.listener.EscalationPublisher;
import dev.eolmae.marketry.domain.stock.service.SectorPriceSnapshotService;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.Test;

class SnapshotRetentionSchedulerTest {
    @Test
    void 한_날짜가_실패해도_다른_오래된_날짜는_계속_정리한다() {
        var service = mock(SectorPriceSnapshotService.class);
        var publisher = mock(EscalationPublisher.class);
        LocalDate today = LocalDate.of(2026, 10, 10);
        LocalDate first = LocalDate.of(2026, 10, 1);
        LocalDate second = LocalDate.of(2026, 10, 2);
        when(service.findCleanupDates(today, 3)).thenReturn(List.of(first, second));
        doThrow(new RuntimeException("failure")).when(service).cleanupSnapshotsForDate(first);
        new SnapshotRetentionScheduler(service, publisher).cleanupSnapshots(today);
        verify(service).cleanupSnapshotsForDate(first);
        verify(service).cleanupSnapshotsForDate(second);
        verify(publisher).report(org.mockito.ArgumentMatchers.anyString(), org.mockito.ArgumentMatchers.any());
    }
}
