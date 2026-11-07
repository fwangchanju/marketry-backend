package kr.co.marketry.domain.view.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.List;
import java.util.Map;
import kr.co.marketry.common.enums.Market;
import kr.co.marketry.domain.stock.entity.MarketOverviewSnapshot;
import kr.co.marketry.domain.stock.repository.MarketOverviewSnapshotRepository;
import kr.co.marketry.domain.stock.service.CalendarDayTimes;
import kr.co.marketry.domain.stock.service.MarketCalendarTimeService;
import org.junit.jupiter.api.Test;

class MarketIndexHistoryServiceTest {
    private static final YearMonth MONTH = YearMonth.of(2026, 9);
    private static final LocalDate DATE = MONTH.atDay(1);
    private final MarketOverviewSnapshotRepository repository = mock(MarketOverviewSnapshotRepository.class);
    private final MarketCalendarTimeService calendar = mock(MarketCalendarTimeService.class);
    private final kr.co.marketry.domain.stock.repository.MarketIndexDailyRepository dailyIndexes =
            mock(kr.co.marketry.domain.stock.repository.MarketIndexDailyRepository.class);
    private final MarketIndexHistoryService service = new MarketIndexHistoryService(repository, calendar, dailyIndexes);

    private static kr.co.marketry.domain.stock.entity.MarketIndexDaily daily(
            String market, LocalDate date, String close) {
        var value = new java.math.BigDecimal(close);
        return kr.co.marketry.domain.stock.entity.MarketIndexDaily.create(
                market, date, value, value, value, value, 1, 5L, date.atStartOfDay());
    }

    @Test
    void 시장_요약이_없는_날짜는_지수_일봉으로_채우고_닛케이는_뺀다() {
        givenSnapshots(List.of());
        when(calendar.resolveAll(anyCollection())).thenReturn(Map.of());
        when(dailyIndexes.findByIdTradeDateBetweenOrderByIdTradeDateAsc(
                        org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any()))
                .thenReturn(List.of(
                        daily("KOSPI", DATE.minusDays(1), "100"),
                        daily("NIKKEI225", DATE, "999"),
                        daily("KOSPI", DATE, "110")));

        var result = service.getClosingHistory(MONTH);

        assertThat(result).singleElement().satisfies(item -> {
            assertThat(item.market()).isEqualTo(Market.KOSPI);
            assertThat(item.indexValue()).isEqualByComparingTo("110");
            assertThat(item.changeValue()).isEqualByComparingTo("10");
            assertThat(item.changeRate()).isEqualByComparingTo("10");
            assertThat(item.snapshotTime().toLocalDate()).isEqualTo(DATE);
        });
    }

    @Test
    void 시장_요약이_있는_날짜는_일봉으로_덮지_않는다() {
        givenSnapshots(List.of(snapshot(Market.KOSPI, 15, 35, "100")));
        when(calendar.resolveAll(anyCollection())).thenReturn(Map.of(DATE, times(15, 30, 15, 40)));
        when(dailyIndexes.findByIdTradeDateBetweenOrderByIdTradeDateAsc(
                        org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any()))
                .thenReturn(List.of(daily("KOSPI", DATE.minusDays(1), "90"), daily("KOSPI", DATE, "95")));

        assertThat(service.getClosingHistory(MONTH)).singleElement().satisfies(item -> assertThat(item.indexValue())
                .isEqualByComparingTo("100"));
    }

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
