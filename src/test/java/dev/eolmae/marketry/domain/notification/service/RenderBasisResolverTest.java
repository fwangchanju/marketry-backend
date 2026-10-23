package dev.eolmae.marketry.domain.notification.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import dev.eolmae.marketry.domain.notification.enums.RenderBasis;
import dev.eolmae.marketry.domain.stock.enums.MarketCalendarStatus;
import dev.eolmae.marketry.domain.view.dto.MarketTradingScheduleResponse;
import dev.eolmae.marketry.domain.view.dto.MarketTradingScheduleResponse.TimeWindow;
import dev.eolmae.marketry.domain.view.service.MarketTradingScheduleService;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class RenderBasisResolverTest {

    private static final LocalDate DATE = LocalDate.of(2026, 10, 23);
    private final MarketTradingScheduleService scheduleService = mock(MarketTradingScheduleService.class);
    private final RenderBasisResolver resolver = new RenderBasisResolver(scheduleService);

    private LocalDateTime at(int hour, int minute) {
        return DATE.atTime(hour, minute);
    }

    private void givenTradingDay() {
        when(scheduleService.getTradingSchedule(DATE))
                .thenReturn(new MarketTradingScheduleResponse(
                        DATE,
                        MarketCalendarStatus.TRADING_DAY,
                        new TimeWindow(at(8, 0), at(9, 0)),
                        new TimeWindow(at(9, 0), at(15, 30)),
                        new TimeWindow(at(15, 40), at(20, 0)),
                        List.of()));
    }

    // 15:30~15:40은 종가, 15:40부터는 애프터 마켓 별도, 그 전에는 기준을 주지 않는다(누적).
    @Test
    void 시각에_맞는_등락률_기준을_고른다() {
        givenTradingDay();

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
    void 거래일이_아니거나_시간표가_비어_있으면_기준을_주지_않는다() {
        when(scheduleService.getTradingSchedule(DATE))
                .thenReturn(MarketTradingScheduleResponse.empty(DATE, MarketCalendarStatus.HOLIDAY));

        assertThat(resolver.resolve(at(15, 35))).isEqualTo(Optional.empty());
    }
}
