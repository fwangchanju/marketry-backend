package kr.co.marketry.domain.stock.util;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.temporal.ChronoUnit;

// 수집 스케줄(평일 지정된 시간대) 기준으로, 특정 시점에 데이터가 있어야 정상인지 판별하는 유틸.
// 시각과 수집 설정값(collect.*)은 전부 인자로 받는다 — LocalDateTime.now()를 내부에서 직접 부르면
// 테스트가 불가능해지므로(docs/rules/testing.md), 호출부가 KstClock.now()와 collect.* 프로퍼티 값을
// 넘긴다.
public final class CollectionChecker {

    private CollectionChecker() {}

    public static boolean isWeekend(LocalDate date) {
        DayOfWeek dayOfWeek = date.getDayOfWeek();
        return dayOfWeek == DayOfWeek.SATURDAY || dayOfWeek == DayOfWeek.SUNDAY;
    }

    public static boolean isWeekend(LocalDateTime dateTime) {
        return isWeekend(dateTime.toLocalDate());
    }

    public static boolean isWeekday(LocalDate date) {
        return !isWeekend(date);
    }

    public static boolean isWeekday(LocalDateTime dateTime) {
        return isWeekday(dateTime.toLocalDate());
    }

    /** from이 주말이면 직전 거래일인(휴무는 고려하지 않고) 금요일로 반환. */
    public static LocalDate previousTradingDay(LocalDate from) {
        return switch (from.getDayOfWeek()) {
            case MONDAY -> from.minusDays(3);
            case SUNDAY -> from.minusDays(2);
            default -> from.minusDays(1);
        };
    }

    /** from에서 거래일 기준 days일 전 반환(휴무는 고려하지 않음). */
    public static LocalDate previousTradingDay(LocalDate from, int days) {
        for (int i = 0; i < days; i++) {
            from = previousTradingDay(from);
        }
        return from;
    }

    /** now가 평일 수집 시간대(startHour:00~endHour:00)인지 여부. */
    public static boolean isTradingTime(LocalDateTime now, int startHour, int endHour) {
        LocalTime nowTime = now.toLocalTime();
        LocalTime startTime = LocalTime.of(startHour, 0);
        LocalTime endTime = LocalTime.of(endHour, 0);
        return isWeekday(now) && !nowTime.isBefore(startTime) && !nowTime.isAfter(endTime);
    }

    /** 수집 시간대면 now를 수집 주기(intervalMinutes) 단위로 절삭, 아니면 직전 수집일의 종료 시각. */
    public static LocalDateTime expectedSnapshotTime(
            LocalDateTime now, int startHour, int endHour, int intervalMinutes) {
        if (isTradingTime(now, startHour, endHour)) {
            int flooredMinute = (now.getMinute() / intervalMinutes) * intervalMinutes;
            return now.withMinute(flooredMinute).truncatedTo(ChronoUnit.MINUTES);
        }

        LocalTime endTime = LocalTime.of(endHour, 0);
        LocalDate today = now.toLocalDate();
        boolean todayAlreadyEnded = isWeekday(today) && now.toLocalTime().isAfter(endTime);
        LocalDate referenceDate = todayAlreadyEnded ? today : previousTradingDay(today);
        return referenceDate.atTime(endTime);
    }
}
