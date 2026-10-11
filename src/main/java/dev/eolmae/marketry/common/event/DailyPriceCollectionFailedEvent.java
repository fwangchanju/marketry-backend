package dev.eolmae.marketry.common.event;

import java.time.LocalDate;
import java.util.List;

/** 알림 발송은 수집의 종료 기한과 분리한다. */
public record DailyPriceCollectionFailedEvent(LocalDate targetDate, String reason, List<String> missingStocks) {}
