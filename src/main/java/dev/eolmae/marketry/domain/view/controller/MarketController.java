package dev.eolmae.marketry.domain.view.controller;

import dev.eolmae.marketry.common.enums.Market;
import dev.eolmae.marketry.domain.stock.enums.AmtQty;
import dev.eolmae.marketry.domain.view.dto.*;
import dev.eolmae.marketry.domain.view.enums.MarketQuery;
import dev.eolmae.marketry.domain.view.enums.RankingType;
import dev.eolmae.marketry.domain.view.service.MarketQueryService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

@RequestMapping("/api")
@RestController
@RequiredArgsConstructor
public class MarketController {

    private final MarketQueryService marketQueryService;

    // ── 메인 대시보드 ─────────────────────────────────────────────────────

    @GetMapping("/summary")
    public MarketSummaryResponse getMarketSummary() {
        return marketQueryService.getMarketSummary();
    }

    // ── 프로그램매매 상위 상세 ────────────────────────────────────────────

    @GetMapping("/program-trading-rankings")
    public SnapshotResponse<ProgramTradingRankingItem> getProgramTradingRankings(
            @RequestParam MarketQuery market, @RequestParam RankingType ranking, @RequestParam AmtQty amtQty) {
        return marketQueryService.getProgramTradingRankings(market, ranking, amtQty);
    }

    // ── 지수 기여도 상위 상세 ─────────────────────────────────────────────

    @GetMapping("/index-contribution")
    public SnapshotResponse<IndexContributionItem> getIndexContribution(@RequestParam Market market) {
        return marketQueryService.getIndexContribution(market);
    }
}
