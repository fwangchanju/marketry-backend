package dev.eolmae.marketry.domain.view.dto;

public record MarketSummaryResponse(
        SnapshotResponse<MarketOverviewItem> marketOverviews,
        SnapshotResponse<InvestorTradingSummaryItem> investorTradingSummaries,
        SnapshotResponse<ProgramTradingRankingItem> programTradingHighlights,
        SnapshotResponse<IndexContributionItem> indexContributionHighlights) {}
