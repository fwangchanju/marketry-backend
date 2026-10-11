package dev.eolmae.marketry.domain.stock.service;

import dev.eolmae.marketry.common.enums.Country;
import dev.eolmae.marketry.common.enums.Zone;
import dev.eolmae.marketry.domain.stock.entity.MarketCalendar;
import dev.eolmae.marketry.domain.stock.entity.TradingSession;
import dev.eolmae.marketry.domain.stock.enums.MarketCalendarStatus;
import dev.eolmae.marketry.domain.stock.properties.MarketHoursProperties;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Stream;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class MarketCalendarTimeService {

    // 긴 연휴(추석 등)도 넘길 수 있는 탐색 범위
    private static final int PREVIOUS_TRADING_DAY_LOOKBACK = 30;

    private final MarketCalendarService marketCalendarService;
    private final MarketHoursProperties marketHoursProperties;

    @Value("${collect.start-hour}")
    private int startHour;

    @Value("${collect.end-hour}")
    private int endHour;

    public CalendarDayTimes resolve(LocalDate date) {
        return resolveStoredCalendar(date, false);
    }

    /** 여러 날을 한 번의 조회로 풀어 날짜별 시간표를 돌려준다. 시간표가 없는 주말은 휴장으로 본다. */
    public Map<LocalDate, CalendarDayTimes> resolveAll(Collection<LocalDate> dates) {
        Map<LocalDate, MarketCalendar> calendars = marketCalendarService.findByCountryAndDateIn(Country.KR, dates);
        Map<LocalDate, CalendarDayTimes> timesByDate = new HashMap<>();
        for (LocalDate date : dates) {
            MarketCalendar calendar = calendars.get(date);
            if (calendar == null
                    && (date.getDayOfWeek() == DayOfWeek.SATURDAY || date.getDayOfWeek() == DayOfWeek.SUNDAY)) {
                timesByDate.put(date, new CalendarDayTimes(true, null, null, null, null, null));
                continue;
            }
            timesByDate.put(date, resolve(date, calendar));
        }
        return timesByDate;
    }

    /** 조회·정리는 수집 시의 기본 시간 처리와 달리, 확인된 거래일만 사용한다. */
    public Map<LocalDate, CalendarDayTimes> resolveTradingDays(Collection<LocalDate> dates) {
        Map<LocalDate, CalendarDayTimes> result = new HashMap<>();
        marketCalendarService.findByCountryAndDateIn(Country.KR, dates).forEach((date, calendar) -> {
            if (calendar.getStatus() == MarketCalendarStatus.TRADING_DAY) {
                result.put(date, resolve(date, calendar));
            }
        });
        return result;
    }

    /**
     * 수집 대상일 판정, 캘린더 활성화, 직전 거래일 탐색이 함께 쓰는 거래일 판정이다. 캘린더가 TRADING_DAY면 거래일, HOLIDAY면
     * 휴장일이고, FAILED이거나 행이 없으면(calendar가 null) 월~금은 거래일, 토·일은 휴장일로 본다.
     */
    public static boolean isTradingDay(LocalDate date, MarketCalendar calendar) {
        if (calendar != null && calendar.getStatus() != MarketCalendarStatus.FAILED) {
            return calendar.getStatus() == MarketCalendarStatus.TRADING_DAY;
        }
        return date.getDayOfWeek() != DayOfWeek.SATURDAY && date.getDayOfWeek() != DayOfWeek.SUNDAY;
    }

    public boolean isTradingDay(LocalDate date) {
        return isTradingDay(
                date,
                marketCalendarService.findByCountryAndDate(Country.KR, date).orElse(null));
    }

    /** {@link #isTradingDay(LocalDate, MarketCalendar)} 기준 거래일만 한 번의 조회로 골라 날짜별 시간표를 돌려준다. */
    public Map<LocalDate, CalendarDayTimes> resolveTradingDaysWithFallback(Collection<LocalDate> dates) {
        Map<LocalDate, MarketCalendar> calendars = marketCalendarService.findByCountryAndDateIn(Country.KR, dates);
        Map<LocalDate, CalendarDayTimes> result = new HashMap<>();
        for (LocalDate date : dates) {
            MarketCalendar calendar = calendars.get(date);
            if (isTradingDay(date, calendar)) {
                result.put(date, resolve(date, calendar));
            }
        }
        return result;
    }

    /** date 이전의 가장 가까운 거래일. 토스 캘린더와 요일 fallback으로 찾으며 단순히 하루 전으로 가지 않는다. */
    public Optional<LocalDate> findPreviousTradingDay(LocalDate date) {
        List<LocalDate> candidates =
                date.minusDays(PREVIOUS_TRADING_DAY_LOOKBACK).datesUntil(date).toList();
        Map<LocalDate, MarketCalendar> calendars = marketCalendarService.findByCountryAndDateIn(Country.KR, candidates);
        for (int index = candidates.size() - 1; index >= 0; index--) {
            LocalDate candidate = candidates.get(index);
            if (isTradingDay(candidate, calendars.get(candidate))) {
                return Optional.of(candidate);
            }
        }
        return Optional.empty();
    }

    /** fromExclusive 다음 날부터 toInclusive까지의 거래일 수 — 주말과 휴장일은 세지 않는다. toInclusive가 더 늦지 않으면 0. */
    public long countTradingDays(LocalDate fromExclusive, LocalDate toInclusive) {
        if (toInclusive.isAfter(fromExclusive) == false) {
            return 0;
        }
        List<LocalDate> dates =
                fromExclusive.plusDays(1).datesUntil(toInclusive.plusDays(1)).toList();
        return marketCalendarService.findByCountryAndDateIn(Country.KR, dates).values().stream()
                .filter(calendar -> calendar.getStatus() == MarketCalendarStatus.TRADING_DAY)
                .count();
    }

    public CalendarDayTimes resolveForCollection(LocalDate date) {
        return resolveStoredCalendar(date, true);
    }

    private CalendarDayTimes resolveStoredCalendar(LocalDate date, boolean checkMissingWeekend) {
        MarketCalendar calendar;
        try {
            calendar =
                    marketCalendarService.findByCountryAndDate(Country.KR, date).orElse(null);
        } catch (Exception exception) {
            log.warn(
                    "[거래일시간표] 조회 실패로 기본 시간 사용 | context : {}|{}",
                    date,
                    exception.getClass().getSimpleName());
            return resolve(date, null);
        }
        if (checkMissingWeekend
                && calendar == null
                && (date.getDayOfWeek() == DayOfWeek.SATURDAY || date.getDayOfWeek() == DayOfWeek.SUNDAY)) {
            return new CalendarDayTimes(true, null, null, null, null, null);
        }
        return resolve(date, calendar);
    }

    public CalendarDayTimes resolve(LocalDate date, MarketCalendar calendar) {
        if (calendar != null && calendar.getStatus() == MarketCalendarStatus.HOLIDAY) {
            return new CalendarDayTimes(true, null, null, null, null, null);
        }
        LocalDateTime defaultClosingStart = date.atTime(marketHoursProperties.closeWindowStart());
        LocalDateTime defaultClosingEnd = date.atTime(marketHoursProperties.afterHoursStart());
        if (calendar == null || calendar.getStatus() == MarketCalendarStatus.FAILED) {
            log.debug(
                    "[거래일시간표] 시간표 없음 또는 실패로 기본 시간 사용 | context : {}|{}",
                    date,
                    calendar == null ? "없음" : calendar.getStatus());
            return new CalendarDayTimes(
                    false,
                    date.atTime(startHour, 0),
                    date.atTime(endHour, 0),
                    defaultClosingStart,
                    defaultClosingStart,
                    defaultClosingEnd);
        }
        var integrated = calendar.getIntegrated();
        var sessions = Stream.of(integrated.preMarket(), integrated.regularMarket(), integrated.afterMarket())
                .filter(Objects::nonNull)
                .toList();
        LocalDateTime collectionStart = sessions.stream()
                .map(TradingSession::startTime)
                .map(this::toDomesticTime)
                .min(LocalDateTime::compareTo)
                .orElseThrow();
        LocalDateTime collectionEnd = sessions.stream()
                .map(TradingSession::endTime)
                .map(this::toDomesticTime)
                .max(LocalDateTime::compareTo)
                .orElseThrow();
        LocalDateTime regularEnd = integrated.regularMarket() == null
                ? defaultClosingStart
                : toDomesticTime(integrated.regularMarket().endTime());
        LocalDateTime closingStart = defaultClosingStart;
        LocalDateTime closingEnd = defaultClosingEnd;
        if (integrated.afterMarket() != null && integrated.afterMarket().singlePriceAuctionEndTime() != null) {
            LocalDateTime auctionEnd = toDomesticTime(integrated.afterMarket().singlePriceAuctionEndTime());
            closingStart = regularEnd;
            closingEnd = auctionEnd;
        }
        return new CalendarDayTimes(false, collectionStart, collectionEnd, regularEnd, closingStart, closingEnd);
    }

    private LocalDateTime toDomesticTime(OffsetDateTime time) {
        return time.atZoneSameInstant(Zone.KST.zoneId()).toLocalDateTime();
    }
}
