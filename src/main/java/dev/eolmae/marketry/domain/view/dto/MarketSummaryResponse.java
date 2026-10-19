package dev.eolmae.marketry.domain.view.dto;

import java.util.List;

public record MarketSummaryResponse(
        SnapshotResponse<MarketOverviewItem> marketOverviews,
        List<MarketOverviewTrendItem> marketOverviewTrends,
        List<MarketSectorTrendItem> sectorOverviewTrends) {}
