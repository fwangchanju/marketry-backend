package kr.co.marketry.domain.stock.dto;

import java.time.LocalDate;
import kr.co.marketry.domain.stock.entity.IntegratedSessions;

public record TossMarketCalendarResponse(Result result) {
    public record Result(Day today, Day previousBusinessDay, Day nextBusinessDay) {}

    public record Day(LocalDate date, IntegratedSessions integrated) {}
}
