package dev.eolmae.marketry.common.event;

import java.util.Set;

/**
 * 거래정지 기록을 맞춘 뒤의 결과. excludedStockCodes는 연속 5거래일에 이른 정지 종목(지금 거래 정지 업종에 있어야 할 종목),
 * resumedStockCodes는 토스가 정지가 아니라고 명시한 종목이다.
 */
public record StockSuspensionSyncedEvent(Set<String> excludedStockCodes, Set<String> resumedStockCodes) {}
