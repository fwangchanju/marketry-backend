package dev.eolmae.marketry.domain.stock.dto;

import java.util.Set;

/**
 * 토스가 알려 준 종목별 거래정지 상태. 정지 중으로 응답한 종목(suspended)과, 정지가 아니라고 명시해 응답한 종목(resumed)을 나눈다.
 * 응답에 아예 없거나 정지 여부가 비어 있는 종목은 둘 어디에도 넣지 않는다 — 재개로 오판하지 않으려는 것이다.
 */
public record StockSuspensionStatus(Set<String> suspended, Set<String> resumed) {}
