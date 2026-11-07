package kr.co.marketry.domain.view.dto;

import java.math.BigDecimal;

/** 마켓 지수 등락률의 현재/이전 시점 짝 — before 시각에 정확히 일치하는 지수 스냅샷이 없으면(장 시작
 * 직후, 수집 gap) before가 null이다. 다른 시점 값으로 조용히 대체하지 않는다. now가 없는 경우(그 시각
 * 지수 스냅샷 자체가 없음)는 이 타입을 아예 안 만들고 상위(SectorChangeRateMarketRanking.index())가
 * null이 되는 것으로 표현한다. */
public record MarketIndexChangeRate(BigDecimal now, BigDecimal before) {}
