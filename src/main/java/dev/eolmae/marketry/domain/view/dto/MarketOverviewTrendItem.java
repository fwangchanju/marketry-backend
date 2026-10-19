package dev.eolmae.marketry.domain.view.dto;

import dev.eolmae.marketry.common.enums.Market;
import dev.eolmae.marketry.domain.stock.entity.MarketOverviewSnapshot;
import java.math.BigDecimal;
import java.time.LocalDate;

public record MarketOverviewTrendItem(Market market, LocalDate date, BigDecimal changeRate) {

    public static MarketOverviewTrendItem from(MarketOverviewSnapshot snapshot) {
        return new MarketOverviewTrendItem(
                snapshot.getMarketType(), snapshot.getSnapshotTime().toLocalDate(), snapshot.getChangeRate());
    }
}
