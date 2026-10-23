package dev.eolmae.marketry.domain.view.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import dev.eolmae.marketry.common.enums.Country;
import dev.eolmae.marketry.domain.stock.entity.IntegratedSessions;
import dev.eolmae.marketry.domain.stock.entity.MarketCalendar;
import dev.eolmae.marketry.domain.stock.entity.TradingSession;
import dev.eolmae.marketry.domain.stock.enums.MarketCalendarStatus;
import dev.eolmae.marketry.domain.stock.service.MarketCalendarService;
import dev.eolmae.marketry.domain.view.dto.MarketTradingScheduleResponse;
import dev.eolmae.marketry.domain.view.dto.MarketTradingScheduleResponse.TimeWindow;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

class MarketTradingScheduleServiceTest {
    private static final LocalDate DATE = LocalDate.of(2025, 11, 13);
    private final MarketCalendarService calendarService = mock(MarketCalendarService.class);
    private final MarketTradingScheduleService service = new MarketTradingScheduleService(calendarService);

    @Test
    void 정상_시간표는_기존_표시와_NXT_필터_구간을_유지한다() {
        saveTrading(new IntegratedSessions(
                session(time(8, 0), time(8, 50), null, time(9, 0)),
                session(time(9, 0), time(15, 20), null, time(15, 30)),
                session(time(15, 30), null, time(15, 40), time(20, 0))));

        MarketTradingScheduleResponse response = service.getTradingSchedule(DATE);

        assertThat(response.status()).isEqualTo(MarketCalendarStatus.TRADING_DAY);
        assertThat(response.preMarket()).isEqualTo(window(8, 0, 9, 0));
        assertThat(response.regularMarket()).isEqualTo(window(9, 0, 15, 30));
        assertThat(response.afterMarket()).isEqualTo(window(15, 40, 20, 0));
        assertThat(response.nxtOnlyWindows()).containsExactly(window(8, 0, 8, 50), window(15, 40, 16, 0));
    }

    // 화면이 NXT 종목만 보여 주는 때와 같은 기준이어야 발송 캡션과 이미지 숫자가 맞는다 — 오전 구간은 정규장 개장(09:00)까지 이어진다.
    @Test
    void 프리장과_애프터_NXT_단독_구간에서만_NXT_단독이다() {
        saveTrading(new IntegratedSessions(
                session(time(8, 0), time(8, 50), null, time(9, 0)),
                session(time(9, 0), time(15, 20), null, time(15, 30)),
                session(time(15, 30), null, time(15, 40), time(20, 0))));

        assertThat(service.isNxtOnly(at(7, 59))).isFalse();
        assertThat(service.isNxtOnly(at(8, 0))).isTrue();
        assertThat(service.isNxtOnly(at(8, 55))).isTrue();
        assertThat(service.isNxtOnly(at(9, 0))).isFalse();
        assertThat(service.isNxtOnly(at(12, 0))).isFalse();
        assertThat(service.isNxtOnly(at(15, 39))).isFalse();
        assertThat(service.isNxtOnly(at(15, 40))).isTrue();
        assertThat(service.isNxtOnly(at(15, 59))).isTrue();
        assertThat(service.isNxtOnly(at(16, 0))).isFalse();
        assertThat(service.isNxtOnly(at(19, 0))).isFalse();
    }

    @Test
    void 거래일이_아니면_NXT_단독이_아니다() {
        when(calendarService.findByCountryAndDate(Country.KR, DATE))
                .thenReturn(Optional.of(MarketCalendar.create(Country.KR, DATE, MarketCalendarStatus.HOLIDAY, null)));

        assertThat(service.isNxtOnly(at(8, 30))).isFalse();
    }

    @Test
    void 수능일은_프리장없이_지연된_시간과_17시까지의_NXT_필터를_준다() {
        saveTrading(new IntegratedSessions(
                null,
                session(time(10, 0), time(16, 20), null, time(16, 30)),
                session(time(16, 30), null, time(16, 40), time(20, 0))));

        MarketTradingScheduleResponse response = service.getTradingSchedule(DATE);

        assertThat(response.preMarket()).isNull();
        assertThat(response.regularMarket()).isEqualTo(window(10, 0, 16, 30));
        assertThat(response.afterMarket()).isEqualTo(window(16, 40, 20, 0));
        assertThat(response.nxtOnlyWindows()).containsExactly(window(16, 40, 17, 0));
    }

    @ParameterizedTest
    @EnumSource(
            value = MarketCalendarStatus.class,
            names = {"HOLIDAY", "FAILED"})
    void 휴장이나_실패는_상태만_전달한다(MarketCalendarStatus status) {
        when(calendarService.findByCountryAndDate(Country.KR, DATE))
                .thenReturn(Optional.of(MarketCalendar.create(Country.KR, DATE, status, null)));

        assertThat(service.getTradingSchedule(DATE)).isEqualTo(MarketTradingScheduleResponse.empty(DATE, status));
    }

    @Test
    void 누락은_프론트가_기존_방식을_사용할_수_있도록_실패로_전달한다() {
        when(calendarService.findByCountryAndDate(Country.KR, DATE)).thenReturn(Optional.empty());

        assertThat(service.getTradingSchedule(DATE))
                .isEqualTo(MarketTradingScheduleResponse.empty(DATE, MarketCalendarStatus.FAILED));
    }

    @Test
    void 거래일의_전체_시간표가_없어도_기존_방식으로_돌아간다() {
        saveTrading(null);

        assertThat(service.getTradingSchedule(DATE))
                .isEqualTo(MarketTradingScheduleResponse.empty(DATE, MarketCalendarStatus.FAILED));
    }

    @Test
    void 단일가_경계가_없으면_NXT_필터를_만들지_않고_저장된_세션만_표시한다() {
        saveTrading(new IntegratedSessions(
                session(time(8, 0), null, null, time(9, 0)),
                session(time(9, 0), null, null, time(15, 30)),
                session(time(16, 0), null, null, time(18, 0))));

        MarketTradingScheduleResponse response = service.getTradingSchedule(DATE);

        assertThat(response.nxtOnlyWindows()).isEmpty();
        assertThat(response.afterMarket()).isEqualTo(window(16, 0, 18, 0));
    }

    @Test
    void 역전되거나_길이가_없는_필터_구간은_제외한다() {
        saveTrading(new IntegratedSessions(
                session(time(8, 0), time(8, 0), null, time(9, 0)),
                session(time(10, 0), null, null, time(16, 30)),
                session(time(16, 30), null, time(17, 10), time(20, 0))));

        assertThat(service.getTradingSchedule(DATE).nxtOnlyWindows()).isEmpty();
    }

    @Test
    void 다른_오프셋으로_저장되어도_같은_시점의_한국_시각을_준다() {
        saveTrading(new IntegratedSessions(
                null,
                session(
                        time(9, 0).withOffsetSameInstant(ZoneOffset.UTC),
                        null,
                        null,
                        time(15, 30).withOffsetSameInstant(ZoneOffset.UTC)),
                session(time(15, 30), null, time(15, 40).withOffsetSameInstant(ZoneOffset.UTC), time(20, 0))));

        MarketTradingScheduleResponse response = service.getTradingSchedule(DATE);

        assertThat(response.regularMarket()).isEqualTo(window(9, 0, 15, 30));
        assertThat(response.nxtOnlyWindows()).containsExactly(window(15, 40, 16, 0));
    }

    private void saveTrading(IntegratedSessions sessions) {
        when(calendarService.findByCountryAndDate(Country.KR, DATE))
                .thenReturn(Optional.of(
                        MarketCalendar.create(Country.KR, DATE, MarketCalendarStatus.TRADING_DAY, sessions)));
    }

    private TradingSession session(
            OffsetDateTime start, OffsetDateTime auctionStart, OffsetDateTime auctionEnd, OffsetDateTime end) {
        return new TradingSession(start, auctionStart, auctionEnd, end);
    }

    private OffsetDateTime time(int hour, int minute) {
        return DATE.atTime(hour, minute).atOffset(ZoneOffset.ofHours(9));
    }

    private TimeWindow window(int startHour, int startMinute, int endHour, int endMinute) {
        return new TimeWindow(DATE.atTime(startHour, startMinute), DATE.atTime(endHour, endMinute));
    }

    private LocalDateTime at(int hour, int minute) {
        return DATE.atTime(hour, minute);
    }
}
