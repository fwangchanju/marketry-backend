package kr.co.marketry.domain.view.dto;

import java.math.BigDecimal;
import java.util.List;
import kr.co.marketry.common.enums.Market;

/** 마켓별 지수 등락률과 대분류 TOP3 섹터 랭킹 — 텔레그램 캡션용으로 확정된 결과. */
public record SectorRankingSummary(Market market, BigDecimal indexChangeRate, List<TopSectorItem> topSectors) {}
