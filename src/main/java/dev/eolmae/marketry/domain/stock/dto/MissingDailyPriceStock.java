package dev.eolmae.marketry.domain.stock.dto;

/** 누락 종목만 반환한다. 이미 저장된 거래소는 재요청하지 않는다. */
public record MissingDailyPriceStock(String stockCode, String stockName, boolean krxMissing, boolean nxtMissing) {}
