package dev.eolmae.marketry.domain.view.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import dev.eolmae.marketry.common.enums.Market;
import dev.eolmae.marketry.domain.stock.entity.MarketOverviewSnapshot;
import dev.eolmae.marketry.domain.stock.repository.MarketOverviewSnapshotRepository;
import dev.eolmae.marketry.domain.stock.service.CalendarDayTimes;
import dev.eolmae.marketry.domain.stock.service.MarketCalendarTimeService;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class MarketIndexHistoryServiceTest {
    private static final YearMonth MONTH = YearMonth.of(2026, 9);
    private static final LocalDate DATE = MONTH.atDay(1);
    private final MarketOverviewSnapshotRepository repository = mock(MarketOverviewSnapshotRepository.class);
    private final MarketCalendarTimeService calendar = mock(MarketCalendarTimeService.class);
    private final MarketIndexHistoryService service = new MarketIndexHistoryService(repository, calendar);

    @Test
    void 지도와_수집_시각이_달라도_마켓별_종가_확정_구간의_마지막_지수를_반환한다() {
        givenSnapshots(List.of(
                snapshot(Market.KOSPI, 15, 25, "90"),
                snapshot(Market.KOSPI, 15, 30, "100"),
                snapshot(Market.KOSDAQ, 15, 30, "200"),
                snapshot(Market.KOSPI, 15, 35, "110"),
                snapshot(Market.KOSPI, 15, 40, "999")));
        when(calendar.resolveAll(anyCollection())).thenReturn(Map.of(DATE, times(15, 30, 15, 40)));

        var result = service.getClosingHistory(MONTH);

        assertThat(result).hasSize(2);
        assertThat(result.get(0).indexValue()).isEqualByComparingTo("110");
        assertThat(result.get(1).indexValue()).isEqualByComparingTo("200");
    }

    @Test
    void 개장_지연일은_그날의_메인마켓_종료_시간을_따른다() {
        givenSnapshots(List.of(snapshot(Market.KOSPI, 15, 35, "100"), snapshot(Market.KOSPI, 16, 35, "110")));
        when(calendar.resolveAll(anyCollection())).thenReturn(Map.of(DATE, times(16, 30, 16, 40)));

        var result = service.getClosingHistory(MONTH);

        assertThat(result).singleElement().satisfies(item -> assertThat(item.indexValue())
                .isEqualByComparingTo("110"));
    }

    @Test
    void 장중_데이터만_있으면_종가로_대체하지_않는다() {
        givenSnapshots(List.of(snapshot(Market.KOSPI, 15, 25, "90")));
        when(calendar.resolveAll(anyCollection())).thenReturn(Map.of(DATE, times(15, 30, 15, 40)));

        assertThat(service.getClosingHistory(MONTH)).isEmpty();
    }

    @Test
    void 휴장일_데이터는_추이에서_제외한다() {
        givenSnapshots(List.of(snapshot(Market.KOSPI, 15, 35, "90")));
        when(calendar.resolveAll(anyCollection()))
                .thenReturn(Map.of(DATE, new CalendarDayTimes(true, null, null, null, null, null)));

        assertThat(service.getClosingHistory(MONTH)).isEmpty();
    }

    private void givenSnapshots(List<MarketOverviewSnapshot> snapshots) {
        when(repository.findBySnapshotTimeGreaterThanEqualAndSnapshotTimeLessThanOrderBySnapshotTimeAsc(
                        MONTH.atDay(1).atStartOfDay(),
                        MONTH.plusMonths(1).atDay(1).atStartOfDay()))
                .thenReturn(snapshots);
    }

    private CalendarDayTimes times(int endHour, int endMinute, int afterHour, int afterMinute) {
        return new CalendarDayTimes(
                false,
                DATE.atTime(8, 0),
                DATE.atTime(20, 0),
                DATE.atTime(endHour, endMinute),
                DATE.atTime(endHour, endMinute),
                DATE.atTime(afterHour, afterMinute));
    }

    private MarketOverviewSnapshot snapshot(Market market, int hour, int minute, String indexValue) {
        var time = DATE.atTime(hour, minute);
        return MarketOverviewSnapshot.create(
                market,
                time,
                new BigDecimal(indexValue),
                BigDecimal.ONE,
                BigDecimal.ONE,
                BigDecimal.TEN,
                "정규장",
                1,
                1,
                0,
                0,
                0,
                time);
    }
}
