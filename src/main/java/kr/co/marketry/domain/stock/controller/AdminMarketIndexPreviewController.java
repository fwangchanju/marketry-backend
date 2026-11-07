package kr.co.marketry.domain.stock.controller;

import java.time.LocalDate;
import java.util.List;
import kr.co.marketry.domain.stock.service.MarketIndexDailyPreviewService;
import kr.co.marketry.domain.stock.service.MarketIndexDailyPreviewService.IndexPreview;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/admin/market-index-preview")
@RequiredArgsConstructor
public class AdminMarketIndexPreviewController {
    private final MarketIndexDailyPreviewService service;

    @GetMapping
    public List<IndexPreview> preview(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        return service.preview(from, to);
    }

    @GetMapping("/stock")
    public IndexPreview previewStock(
            @RequestParam String code,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        return service.previewStock(code, from, to);
    }
}
