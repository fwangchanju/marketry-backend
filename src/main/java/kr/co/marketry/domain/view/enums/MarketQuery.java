package kr.co.marketry.domain.view.enums;

import java.util.List;
import kr.co.marketry.common.enums.Market;

public enum MarketQuery {
    KOSPI,
    KOSDAQ,
    ALL_STOCK;

    public List<Market> toMarkets() {
        return switch (this) {
            case ALL_STOCK -> List.of(Market.KOSPI, Market.KOSDAQ);
            default -> List.of(Market.valueOf(name()));
        };
    }
}
