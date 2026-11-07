package kr.co.marketry.domain.view.controller;

import java.time.YearMonth;
import java.util.List;
import kr.co.marketry.domain.view.dto.MarketOverviewItem;
import kr.co.marketry.domain.view.dto.MarketSummaryResponse;
import kr.co.marketry.domain.view.service.MarketIndexHistoryService;
import kr.co.marketry.domain.view.service.MarketQueryService;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RequestMapping("/api")
@RestController
@RequiredArgsConstructor
public class MarketController {

    private final MarketQueryService marketQueryService;
    private final MarketIndexHistoryService marketIndexHistoryService;

    @GetMapping("/market-index-history")
    public List<MarketOverviewItem> getMarketIndexHistory(
            @RequestParam @DateTimeFormat(pattern = "yyyy-MM") YearMonth month) {
        return marketIndexHistoryService.getClosingHistory(month);
    }

    @GetMapping("/summary")
    public MarketSummaryResponse getMarketSummary() {
        return marketQueryService.getMarketSummary();
    }
}
