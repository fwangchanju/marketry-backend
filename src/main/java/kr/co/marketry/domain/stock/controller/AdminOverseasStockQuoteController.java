package kr.co.marketry.domain.stock.controller;

import java.time.LocalDateTime;
import java.util.List;
import kr.co.marketry.domain.stock.service.OverseasStockQuoteService;
import kr.co.marketry.domain.stock.service.OverseasStockQuoteService.QuoteSnapshot;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/admin/overseas-stock-quotes")
@RequiredArgsConstructor
public class AdminOverseasStockQuoteController {
    private final OverseasStockQuoteService service;

    @GetMapping("/{market}")
    public QuoteSnapshot findQuotes(
            @PathVariable String market,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME)
                    LocalDateTime snapshotTime) {
        return service.findQuotes(market, snapshotTime);
    }

    @GetMapping("/{market}/snapshots")
    public List<LocalDateTime> findSnapshotTimes(@PathVariable String market) {
        return service.findSnapshotTimes(market);
    }
}
