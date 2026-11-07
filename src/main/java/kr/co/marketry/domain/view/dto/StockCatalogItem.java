package kr.co.marketry.domain.view.dto;

import kr.co.marketry.common.enums.Market;

/** 로그인 없이 읽는 종목 공통 정보 — 회원별 분류나 별칭은 담지 않는다. */
public record StockCatalogItem(String stockCode, Market market, boolean nxtEnabled, String industryName) {}
