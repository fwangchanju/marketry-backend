package kr.co.marketry.domain.stock.entity;

import com.fasterxml.jackson.annotation.JsonFormat;
import java.time.OffsetDateTime;

public record TradingSession(
        @JsonFormat(without = JsonFormat.Feature.ADJUST_DATES_TO_CONTEXT_TIME_ZONE)
        OffsetDateTime startTime,

        @JsonFormat(without = JsonFormat.Feature.ADJUST_DATES_TO_CONTEXT_TIME_ZONE)
        OffsetDateTime singlePriceAuctionStartTime,

        @JsonFormat(without = JsonFormat.Feature.ADJUST_DATES_TO_CONTEXT_TIME_ZONE)
        OffsetDateTime singlePriceAuctionEndTime,

        @JsonFormat(without = JsonFormat.Feature.ADJUST_DATES_TO_CONTEXT_TIME_ZONE)
        OffsetDateTime endTime) {}
