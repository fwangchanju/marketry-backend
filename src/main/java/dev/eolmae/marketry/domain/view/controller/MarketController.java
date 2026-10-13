package dev.eolmae.marketry.domain.view.controller;

import dev.eolmae.marketry.domain.view.dto.MarketSummaryResponse;
import dev.eolmae.marketry.domain.view.service.MarketQueryService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RequestMapping("/api")
@RestController
@RequiredArgsConstructor
public class MarketController {

    private final MarketQueryService marketQueryService;

    @GetMapping("/summary")
    public MarketSummaryResponse getMarketSummary() {
        return marketQueryService.getMarketSummary();
    }
}
