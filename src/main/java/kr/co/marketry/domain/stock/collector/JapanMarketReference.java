package kr.co.marketry.domain.stock.collector;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.Set;
import java.util.stream.Collectors;

/** JPX가 공지한 2026~2027 휴장일과 지도 구성 종목의 기준 목록이다. */
public final class JapanMarketReference {
    private static final Set<LocalDate> HOLIDAYS = Set.of(
            LocalDate.parse("2026-01-01"),
            LocalDate.parse("2026-01-02"),
            LocalDate.parse("2026-01-12"),
            LocalDate.parse("2026-02-11"),
            LocalDate.parse("2026-02-23"),
            LocalDate.parse("2026-03-20"),
            LocalDate.parse("2026-04-29"),
            LocalDate.parse("2026-05-04"),
            LocalDate.parse("2026-05-05"),
            LocalDate.parse("2026-05-06"),
            LocalDate.parse("2026-07-20"),
            LocalDate.parse("2026-08-11"),
            LocalDate.parse("2026-09-21"),
            LocalDate.parse("2026-09-22"),
            LocalDate.parse("2026-09-23"),
            LocalDate.parse("2026-10-12"),
            LocalDate.parse("2026-11-03"),
            LocalDate.parse("2026-11-23"),
            LocalDate.parse("2026-12-31"),
            LocalDate.parse("2027-01-01"),
            LocalDate.parse("2027-01-11"),
            LocalDate.parse("2027-02-11"),
            LocalDate.parse("2027-02-23"),
            LocalDate.parse("2027-03-22"),
            LocalDate.parse("2027-04-29"),
            LocalDate.parse("2027-05-03"),
            LocalDate.parse("2027-05-04"),
            LocalDate.parse("2027-05-05"),
            LocalDate.parse("2027-07-19"),
            LocalDate.parse("2027-08-11"),
            LocalDate.parse("2027-09-20"),
            LocalDate.parse("2027-09-23"),
            LocalDate.parse("2027-10-11"),
            LocalDate.parse("2027-11-03"),
            LocalDate.parse("2027-11-23"),
            LocalDate.parse("2027-12-31"));

    private JapanMarketReference() {}

    public static boolean isKnownHoliday(LocalDate date) {
        return date.getDayOfWeek() == DayOfWeek.SATURDAY
                || date.getDayOfWeek() == DayOfWeek.SUNDAY
                || HOLIDAYS.contains(date);
    }

    public static LocalDate previousTradingDay(LocalDate date) {
        LocalDate candidate = date.minus(1, ChronoUnit.DAYS);
        while (isKnownHoliday(candidate)) candidate = candidate.minusDays(1);
        return candidate;
    }

    static Set<String> constituentCodes() {
        try (var input = JapanMarketReference.class.getResourceAsStream("/japan-constituents.txt")) {
            if (input == null) throw new IllegalStateException("일본 지도 종목 기준 파일이 없습니다");
            Set<String> codes = new String(input.readAllBytes(), StandardCharsets.UTF_8)
                    .lines()
                    .filter(line -> !line.isBlank())
                    .collect(Collectors.toUnmodifiableSet());
            if (codes.size() != 225) throw new IllegalStateException("일본 지도 종목 기준이 225개가 아닙니다");
            return codes;
        } catch (IOException error) {
            throw new IllegalStateException("일본 지도 종목 기준을 읽지 못했습니다", error);
        }
    }
}
