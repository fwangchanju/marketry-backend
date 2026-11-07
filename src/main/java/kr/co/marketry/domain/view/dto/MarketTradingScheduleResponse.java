package kr.co.marketry.domain.view.dto;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import kr.co.marketry.domain.stock.enums.MarketCalendarStatus;

public record MarketTradingScheduleResponse(
        LocalDate date,
        MarketCalendarStatus status,
        TimeWindow preMarket,
        TimeWindow regularMarket,
        TimeWindow afterMarket,
        List<TimeWindow> nxtOnlyWindows) {

    public static MarketTradingScheduleResponse empty(LocalDate date, MarketCalendarStatus status) {
        return new MarketTradingScheduleResponse(date, status, null, null, null, List.of());
    }

    public record TimeWindow(LocalDateTime startTime, LocalDateTime endTime) {}
}
