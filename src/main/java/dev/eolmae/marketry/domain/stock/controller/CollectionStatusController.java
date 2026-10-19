package dev.eolmae.marketry.domain.stock.controller;

import dev.eolmae.marketry.common.enums.Zone;
import dev.eolmae.marketry.common.exception.BusinessException;
import dev.eolmae.marketry.domain.stock.collector.MarketCalendarCollector;
import dev.eolmae.marketry.domain.stock.scheduler.CollectionScheduler;
import dev.eolmae.marketry.domain.stock.scheduler.CollectionScheduler.CollectionStatus;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** 자격 증명과 원문 예외를 내보내지 않는 운영 수집 상태 조회. */
@RestController
@RequestMapping("/api/operations/collection-status")
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
