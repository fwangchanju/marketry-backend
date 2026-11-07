package kr.co.marketry.domain.view.dto;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import kr.co.marketry.common.enums.Market;
import kr.co.marketry.domain.stock.entity.MarketOverviewSnapshot;

public record MarketOverviewItem(
        Market market,
        String marketStatus,
        BigDecimal indexValue,
        BigDecimal changeValue,
        BigDecimal changeRate,
        BigDecimal tradingValue,
        int upperLimitCount,
        int lowerLimitCount,
        int advancers,
        int decliners,
        int unchangedCount,
        LocalDateTime snapshotTime) {

    public static MarketOverviewItem from(MarketOverviewSnapshot snapshot) {
        return new MarketOverviewItem(
                snapshot.getMarketType(),
                snapshot.getMarketStatus(),
                snapshot.getIndexValue(),
                snapshot.getChangeValue(),
                snapshot.getChangeRate(),
                snapshot.getTradingValue(),
                snapshot.getUpperLimitCount(),
                snapshot.getLowerLimitCount(),
                snapshot.getAdvancers(),
                snapshot.getDecliners(),
                snapshot.getUnchangedCount(),
                snapshot.getSnapshotTime());
    }
}
