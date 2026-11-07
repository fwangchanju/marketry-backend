package kr.co.marketry.domain.custom.dto;

import java.math.BigDecimal;
import kr.co.marketry.common.enums.Market;

public record StockSectorListItem(
        String stockCode,
        Market market,
        String stockName,
        boolean nxtEnabled,
        String alias,
        BigDecimal totalMarketValue,
        String marketValueTier,
        String industryName,
        String parentSectorName,
        String sectorName,
        Long sectorId) {}
