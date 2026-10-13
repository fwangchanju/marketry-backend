package dev.eolmae.marketry.domain.stock.dto;

import java.util.List;

public record TossStockDetailResponse(List<Item> result) {
    public record Item(String symbol, KoreanMarketDetail koreanMarketDetail) {}

    /** 국내 종목에만 있다. 거래정지 여부는 KRX 기준이고 정보가 없으면 null이다. */
    public record KoreanMarketDetail(Boolean krxTradingSuspended) {}
}
