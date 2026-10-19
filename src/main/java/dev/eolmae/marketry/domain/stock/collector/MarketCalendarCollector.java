package dev.eolmae.marketry.domain.stock.collector;

import dev.eolmae.marketry.common.enums.Country;
import dev.eolmae.marketry.common.enums.Zone;
import dev.eolmae.marketry.common.exception.BadRequestException;
import dev.eolmae.marketry.common.exception.BusinessException;
import dev.eolmae.marketry.common.exception.ErrorCode;
import dev.eolmae.marketry.domain.stock.client.TossMarketCalendarClient;
import dev.eolmae.marketry.domain.stock.dto.TossMarketCalendarResponse;
import dev.eolmae.marketry.domain.stock.dto.TossMarketCalendarResponse.Day;
import dev.eolmae.marketry.domain.stock.entity.IntegratedSessions;
import dev.eolmae.marketry.domain.stock.entity.MarketCalendar;
import dev.eolmae.marketry.domain.stock.entity.TradingSession;
import dev.eolmae.marketry.domain.stock.enums.MarketCalendarStatus;
import dev.eolmae.marketry.domain.stock.service.MarketCalendarService;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class MarketCalendarCollector {
    private final TossMarketCalendarClient client;
    private final MarketCalendarService service;

    /** 운영 진단용: 토스 응답과 시간표 형식만 확인하고 DB는 변경하지 않는다. */
    public void probe(LocalDate date) {
        validateResponse(date, client.fetch(date));
    }

    public boolean collect(LocalDate date) {
        try {
            service.saveSuccess(Country.KR, validateResponse(date, client.fetch(date)));
            log.info("[시장 시간표 저장 완료] | context : KR|{}", date);
            return true;
        } catch (Exception e) {
            String failure = e instanceof BusinessException businessException
                    ? businessException.createLogMessage()
                    : e.getClass().getSimpleName();
            log.warn("[시장 시간표 조회 또는 저장 실패, 기본 시간 사용] | context : KR|{}|{}", date, failure);
            saveFailureSafely(date);
            return false;
        }
    }

    private void saveFailureSafely(LocalDate date) {
        try {
            service.saveFailure(Country.KR, date);
        } catch (Exception e) {
            log.warn(
                    "[시장 시간표 실패 상태 저장 실패, 기본 시간 사용] | context : KR|{}|{}",
                    date,
                    e.getClass().getSimpleName());
        }
    }

    List<MarketCalendar> validateResponse(LocalDate date, TossMarketCalendarResponse response) {
        if (response == null || response.result() == null) {
            throw invalidResponse();
        }
        var result = response.result();
        if (result.today() == null && (result.previousBusinessDay() == null || result.nextBusinessDay() == null)) {
            throw invalidResponse();
        }
        List<MarketCalendar> calendars = new ArrayList<>();
        Set<LocalDate> dates = new HashSet<>();
        if (result.today() != null) {
            validateDay(result.today(), dates, calendars);
            if (date.equals(result.today().date()) == false) {
                throw invalidResponse();
            }
        } else {
            calendars.add(MarketCalendar.create(Country.KR, date, MarketCalendarStatus.HOLIDAY, null));
            dates.add(date);
        }
        if (result.previousBusinessDay() != null) {
            validateDay(result.previousBusinessDay(), dates, calendars);
            if (result.previousBusinessDay().date().isBefore(date) == false) {
                throw invalidResponse();
            }
        }
        if (result.nextBusinessDay() != null) {
            validateDay(result.nextBusinessDay(), dates, calendars);
            if (result.nextBusinessDay().date().isAfter(date) == false) {
                throw invalidResponse();
            }
        }
        return calendars;
    }

    private void validateDay(Day day, Set<LocalDate> dates, List<MarketCalendar> calendars) {
        if (day.date() == null || dates.add(day.date()) == false) {
            throw invalidResponse();
        }
        IntegratedSessions integrated = day.integrated();
        if (integrated != null) {
            if (integrated.preMarket() == null
                    && integrated.regularMarket() == null
                    && integrated.afterMarket() == null) {
                throw invalidResponse();
            }
            validateSession(day.date(), integrated.preMarket());
            validateSession(day.date(), integrated.regularMarket());
            validateSession(day.date(), integrated.afterMarket());
        }
        calendars.add(MarketCalendar.create(
                Country.KR,
                day.date(),
                integrated == null ? MarketCalendarStatus.HOLIDAY : MarketCalendarStatus.TRADING_DAY,
                integrated));
    }

    private void validateSession(LocalDate date, TradingSession session) {
        if (session == null) {
            return;
        }
        if (session.startTime() == null
                || session.endTime() == null
                || session.startTime().isBefore(session.endTime()) == false) {
            throw invalidResponse();
        }
        validateTime(date, session.startTime(), session);
        validateTime(date, session.endTime(), session);
        validateTime(date, session.singlePriceAuctionStartTime(), session);
        validateTime(date, session.singlePriceAuctionEndTime(), session);
        if (session.singlePriceAuctionStartTime() != null
                && session.singlePriceAuctionEndTime() != null
                && session.singlePriceAuctionStartTime().isAfter(session.singlePriceAuctionEndTime())) {
            throw invalidResponse();
        }
    }

    private void validateTime(LocalDate date, OffsetDateTime time, TradingSession session) {
        if (time == null) {
            return;
        }
        if (time.atZoneSameInstant(Zone.KST.zoneId()).toLocalDate().equals(date) == false
                || time.isBefore(session.startTime())
                || time.isAfter(session.endTime())) {
            throw invalidResponse();
        }
    }

    private BadRequestException invalidResponse() {
        return new BadRequestException(ErrorCode.MARKET_CALENDAR_RESPONSE_INVALID);
    }
}
