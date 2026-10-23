package dev.eolmae.marketry.domain.notification.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import dev.eolmae.marketry.common.enums.Country;
import dev.eolmae.marketry.domain.notification.enums.RenderBasis;
import dev.eolmae.marketry.domain.stock.entity.MarketCalendar;
import dev.eolmae.marketry.domain.stock.enums.MarketCalendarStatus;
import dev.eolmae.marketry.domain.stock.properties.MarketHoursProperties;
import dev.eolmae.marketry.domain.stock.service.CalendarDayTimes;
import dev.eolmae.marketry.domain.stock.service.MarketCalendarService;
import dev.eolmae.marketry.domain.stock.service.MarketCalendarTimeService;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class RenderBasisResolverTest {

    private static final LocalDate DATE = LocalDate.of(2026, 10, 23);

    private LocalDateTime at(int hour, int minute) {
        return DATE.atTime(hour, minute);
    }

    // 15:30~15:40은 종가, 15:40부터는 애프터 마켓 별도, 그 전에는 기준을 주지 않는다(누적).
    @Test
    void 마감_구간에_맞는_등락률_기준을_고른다() {
        MarketCalendarTimeService timeService = mock(MarketCalendarTimeService.class);
        when(timeService.resolve(DATE))
                .thenReturn(new CalendarDayTimes(false, at(8, 0), at(20, 0), at(15, 30), at(15, 30), at(15, 40)));
        RenderBasisResolver resolver = new RenderBasisResolver(timeService);

        assertThat(resolver.resolve(at(8, 5))).isEmpty();
        assertThat(resolver.resolve(at(15, 5))).isEmpty();
        assertThat(resolver.resolve(at(15, 29))).isEmpty();
        assertThat(resolver.resolve(at(15, 30))).contains(RenderBasis.CLOSE);
        assertThat(resolver.resolve(at(15, 35))).contains(RenderBasis.CLOSE);
        assertThat(resolver.resolve(at(15, 40))).contains(RenderBasis.AFTER_HOURS);
        assertThat(resolver.resolve(at(16, 5))).contains(RenderBasis.AFTER_HOURS);
        assertThat(resolver.resolve(at(20, 5))).contains(RenderBasis.AFTER_HOURS);
    }

    @Test
    void 휴장일이면_기준을_주지_않는다() {
        MarketCalendarTimeService timeService = mock(MarketCalendarTimeService.class);
        when(timeService.resolve(DATE)).thenReturn(new CalendarDayTimes(true, null, null, null, null, null));

        assertThat(new RenderBasisResolver(timeService).resolve(at(16, 5))).isEqualTo(Optional.empty());
    }

    // 거래소 시간표가 없거나 수집에 실패한 날에도 설정의 기본 시각(15:30·15:40)으로 판정해야 한다 — 시간표가 비었다고 누적으로 남으면 안 된다.
    @Test
    void 시간표가_없거나_실패한_날도_기본_시각으로_별도를_고른다() {
        MarketCalendarService calendarService = mock(MarketCalendarService.class);
        MarketCalendarTimeService timeService = new MarketCalendarTimeService(
                calendarService, new MarketHoursProperties(LocalTime.of(15, 30), LocalTime.of(15, 40)));
        RenderBasisResolver resolver = new RenderBasisResolver(timeService);

        when(calendarService.findByCountryAndDate(Country.KR, DATE)).thenReturn(Optional.empty());
        assertThat(resolver.resolve(at(15, 35))).contains(RenderBasis.CLOSE);
        assertThat(resolver.resolve(at(16, 55))).contains(RenderBasis.AFTER_HOURS);

        when(calendarService.findByCountryAndDate(Country.KR, DATE))
                .thenReturn(Optional.of(MarketCalendar.create(Country.KR, DATE, MarketCalendarStatus.FAILED, null)));
        assertThat(resolver.resolve(at(15, 5))).isEmpty();
        assertThat(resolver.resolve(at(16, 55))).contains(RenderBasis.AFTER_HOURS);
    }
}
