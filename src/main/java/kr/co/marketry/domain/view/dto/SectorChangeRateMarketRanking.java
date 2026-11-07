package kr.co.marketry.domain.view.dto;

import java.util.List;
import kr.co.marketry.common.enums.Market;

public record SectorChangeRateMarketRanking(
        Market market, List<SectorChangeRateItem> items, MarketIndexChangeRate index) {

    /** index 없이 랭킹만 다루는 호출부(스냅샷 서비스 자체는 지수 개념을 모른다)용. */
    public SectorChangeRateMarketRanking(Market market, List<SectorChangeRateItem> items) {
        this(market, items, null);
    }
}
