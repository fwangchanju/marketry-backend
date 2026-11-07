package kr.co.marketry.domain.view.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import kr.co.marketry.common.enums.Market;
import kr.co.marketry.domain.stock.entity.MarketOverviewSnapshot;

public record MarketOverviewTrendItem(Market market, LocalDate date, BigDecimal changeRate) {

    public static MarketOverviewTrendItem from(MarketOverviewSnapshot snapshot) {
        return new MarketOverviewTrendItem(
                snapshot.getMarketType(), snapshot.getSnapshotTime().toLocalDate(), snapshot.getChangeRate());
    }
}
