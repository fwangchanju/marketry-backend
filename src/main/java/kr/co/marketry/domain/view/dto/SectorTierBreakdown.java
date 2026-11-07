package kr.co.marketry.domain.view.dto;

import java.math.BigDecimal;

// 섹터(하위 섹터 재귀 포함) 하나의, 시가총액 구간 하나에 대한 등락률 원시 합계(분자/분모) — 이미
// 나눠진 평균이 아니다. 호출부가 원하는 구간들만 골라 weightedSum/totalValue, simpleSum/itemCount를
// 각각 합산한 뒤 마지막에 한 번만 나눠야 여러 구간 조합(예: 소형주 제외)에서도 정확한 평균이 나온다.
public record SectorTierBreakdown(
        Long tierId,
        String tierLabel,
        BigDecimal weightedSum,
        BigDecimal totalValue,
        BigDecimal simpleSum,
        Integer itemCount) {}
