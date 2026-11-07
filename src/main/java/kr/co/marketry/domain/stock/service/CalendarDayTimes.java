package kr.co.marketry.domain.stock.service;

import java.time.LocalDateTime;

public record CalendarDayTimes(
        boolean holiday,
        LocalDateTime collectionStart,
        LocalDateTime collectionEnd,
        LocalDateTime regularMarketEnd,
        LocalDateTime closingWindowStart,
        LocalDateTime closingWindowEnd) {}
