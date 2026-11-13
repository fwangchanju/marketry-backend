package kr.co.marketry.domain.stock.collector;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import kr.co.marketry.domain.stock.client.YahooFinanceQuoteClient.IndexSession;

/** 야후가 알려 주는 정규장 종료 시각으로 최신 일봉 날짜의 마감 시각을 구한다. */
public final class UsaMarketSession {
    public static final ZoneId NEW_YORK = ZoneId.of("America/New_York");
    private static final LocalTime STANDARD_CLOSE = LocalTime.of(16, 0);

    private UsaMarketSession() {}

    /**
     * 최신 일봉 날짜의 마감 시각. 야후의 정규장 종료 시각은 그 날짜의 것이거나, 장이 열리기 전이나 마감 뒤에는 이미 다음 거래일의 것이다
     * (2026-11-13 개장 전에 최신 일봉은 11/12인데 종료 시각은 11/13 16:00 ET였다).
     *
     * <p>종료 시각이 그 날짜의 것이면 그대로 쓴다. 이미 다음 거래일의 것이면 그 날짜의 마감은 지난 것이고, 정확한 마감 시각을 알 수
     * 없어서 16:00 ET로 본다. 조기 마감일에는 실제보다 늦게 보므로 저장이 늦어질 뿐 일찍 저장하지는 않는다. 종료 시각이 최신 일봉
     * 날짜보다 앞서면 값이 이상한 것이라 null이다.
     */
    public static Instant closeTimeOf(IndexSession session) {
        LocalDate day = session.latestBarDate();
        LocalDate endDay = session.regularEnd().atZone(NEW_YORK).toLocalDate();
        if (endDay.equals(day)) {
            return session.regularEnd();
        }
        if (endDay.isAfter(day)) {
            return day.atTime(STANDARD_CLOSE).atZone(NEW_YORK).toInstant();
        }
        return null;
    }
}
