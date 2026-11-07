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

    /** 시장 요약이 없는 과거 날짜의 지수. 상승·하락 종목 수는 없어서 0이고 화면은 이 값을 쓰지 않는다. */
    public static MarketOverviewItem fromDaily(
            Market market,
            BigDecimal indexValue,
            BigDecimal changeValue,
            BigDecimal changeRate,
            BigDecimal tradingValue,
            LocalDateTime snapshotTime) {
        return new MarketOverviewItem(
                market, "", indexValue, changeValue, changeRate, tradingValue, 0, 0, 0, 0, 0, snapshotTime);
    }

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
