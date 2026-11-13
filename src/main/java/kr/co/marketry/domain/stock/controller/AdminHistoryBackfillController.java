package kr.co.marketry.domain.stock.controller;

import java.time.LocalDate;
import kr.co.marketry.domain.stock.service.HistoryBackfillService;
import kr.co.marketry.domain.stock.service.HistoryBackfillService.Status;
import kr.co.marketry.domain.stock.service.JapanHistoryBackfillService;
import kr.co.marketry.domain.stock.service.UsaHistoryBackfillService;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/admin/history-backfill")
@RequiredArgsConstructor
public class AdminHistoryBackfillController {
    private final HistoryBackfillService service;
    private final JapanHistoryBackfillService japanService;
    private final UsaHistoryBackfillService usaService;

    @GetMapping
    public Status status() {
        return service.status();
    }

    @GetMapping("/japan")
    public JapanHistoryBackfillService.Status japanStatus() {
        return japanService.status();
    }

    @PostMapping("/japan")
    public JapanHistoryBackfillService.Status startJapan(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        return japanService.start(from, to);
    }

    @GetMapping("/usa")
    public UsaHistoryBackfillService.Status usaStatus() {
        return usaService.status();
    }

    @PostMapping("/usa")
    public UsaHistoryBackfillService.Status startUsa(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        return usaService.start(from, to);
    }

    @PostMapping("/{job}")
    public Status start(
            @PathVariable String job,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        return service.start(job, from, to);
    }
}
