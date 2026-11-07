package kr.co.marketry.domain.stock.collector;

import java.time.LocalDate;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.time.format.ResolverStyle;
import java.util.List;

record MarketCalendarManualOptions(LocalDate startDate, LocalDate endDate) {
    private static final DateTimeFormatter MONTH_FORMAT =
            DateTimeFormatter.ofPattern("uuuuMM").withResolverStyle(ResolverStyle.STRICT);

    static MarketCalendarManualOptions resolve(String month, String date, String endDate, LocalDate today) {
        if (month.isBlank() == false) {
            if (date.isBlank() == false || endDate.isBlank() == false) {
                throw new AssertionError("calendar.month는 calendar.date / calendar.end-date와 함께 지정할 수 없습니다.");
            }
            YearMonth requestedMonth = parseMonth(month);
            if (requestedMonth.isAfter(YearMonth.from(today))) {
                throw new AssertionError("미래 월은 수집할 수 없습니다.");
            }
            LocalDate lastDate = requestedMonth.atEndOfMonth();
            if (lastDate.isAfter(today)) {
                lastDate = today;
            }
            return new MarketCalendarManualOptions(requestedMonth.atDay(1), lastDate);
        }
        LocalDate start = parseDate(date, today, today);
        LocalDate end = parseDate(endDate, start, today);
        if (end.isBefore(start)) {
            throw new AssertionError("종료일은 시작일보다 빠를 수 없습니다.");
        }
        return new MarketCalendarManualOptions(start, end);
    }

    List<LocalDate> dates() {
        return startDate.datesUntil(endDate.plusDays(1)).toList();
    }

    private static YearMonth parseMonth(String value) {
        if (value.matches("[0-9]{6}") == false) {
            throw new AssertionError("calendar.month는 202601과 같은 여섯 자리 yyyyMM이어야 합니다.");
        }
        try {
            YearMonth month = YearMonth.parse(value, MONTH_FORMAT);
            if (month.getYear() == 0) {
                throw new AssertionError("calendar.month의 연도는 1 이상이어야 합니다.");
            }
            return month;
        } catch (DateTimeParseException e) {
            throw new AssertionError("calendar.month는 유효한 yyyyMM이어야 합니다.");
        }
    }

    private static LocalDate parseDate(String value, LocalDate defaultDate, LocalDate today) {
        if (value.isBlank()) {
            return defaultDate;
        }
        if (value.equals("today")) {
            return today;
        }
        if (value.matches("[0-9]{4}-[0-9]{2}-[0-9]{2}") == false) {
            throw new AssertionError("calendar.date / calendar.end-date는 yyyy-MM-dd 또는 today여야 합니다.");
        }
        try {
            return LocalDate.parse(value);
        } catch (DateTimeParseException e) {
            throw new AssertionError("calendar.date / calendar.end-date는 유효한 yyyy-MM-dd 또는 today여야 합니다.");
        }
    }
}
