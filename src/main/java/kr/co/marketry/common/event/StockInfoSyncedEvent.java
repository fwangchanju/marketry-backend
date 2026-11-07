package kr.co.marketry.common.event;

import java.util.List;

/** stock_info에 새로 등록된 코스피·코스닥 일반주 코드 목록. */
public record StockInfoSyncedEvent(List<String> stockCodes) {}
