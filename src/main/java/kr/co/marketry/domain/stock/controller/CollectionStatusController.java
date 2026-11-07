package kr.co.marketry.domain.stock.controller;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import kr.co.marketry.common.enums.Zone;
import kr.co.marketry.common.exception.BusinessException;
import kr.co.marketry.domain.stock.collector.MarketCalendarCollector;
import kr.co.marketry.domain.stock.scheduler.CollectionScheduler;
import kr.co.marketry.domain.stock.scheduler.CollectionScheduler.CollectionStatus;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** 운영 수집 상태 조회 — 내부 상태를 담으므로 /api/admin/** 규칙(ADMIN 전용)을 그대로 쓴다. */
@RestController
@RequestMapping("/api/admin/operations/collection-status")
@RequiredArgsConstructor
public class CollectionStatusController {

    private final CollectionScheduler scheduler;
    private final MarketCalendarCollector calendarCollector;
    private Probe lastProbe;

    @GetMapping
    public Status get() {
        LocalDate date = LocalDate.now(Zone.KST.zoneId());
        Probe probe = probe(date);
        return new Status(scheduler.lastCollectionStatus(), date, probe.failure() == null, probe.failure());
    }

    private synchronized Probe probe(LocalDate date) {
        Instant now = Instant.now();
        if (lastProbe != null
                && lastProbe.date().equals(date)
                && now.isBefore(lastProbe.checkedAt().plus(Duration.ofMinutes(5)))) {
            return lastProbe;
        }
        String calendarFailure = null;
        try {
            calendarCollector.probe(date);
        } catch (Exception exception) {
            calendarFailure = exception instanceof BusinessException businessException
                    ? businessException.createLogMessage()
                    : exception.getClass().getSimpleName();
        }
        lastProbe = new Probe(date, now, calendarFailure);
        return lastProbe;
    }

    private record Probe(LocalDate date, Instant checkedAt, String failure) {}

    public record Status(
            CollectionStatus lastCollection,
            LocalDate calendarDate,
            boolean calendarProbeSuccess,
            String calendarFailure) {}
}
