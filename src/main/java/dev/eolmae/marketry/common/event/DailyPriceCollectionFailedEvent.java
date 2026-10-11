package dev.eolmae.marketry.common.event;

import java.time.LocalDate;
import java.util.List;

/** 재시도 후 남은 실패 또는 제한 시간 초과를 알린다. 제한 시간 초과에는 종목 목록이 없다. */
public record DailyPriceCollectionFailedEvent(LocalDate targetDate, String reason, List<String> failedStocks) {}
