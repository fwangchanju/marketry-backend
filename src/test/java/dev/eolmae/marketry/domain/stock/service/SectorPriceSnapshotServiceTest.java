package dev.eolmae.marketry.domain.stock.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import dev.eolmae.marketry.common.enums.Country;
import dev.eolmae.marketry.common.enums.Market;
import dev.eolmae.marketry.domain.stock.entity.IntegratedSessions;
import dev.eolmae.marketry.domain.stock.entity.MarketCalendar;
import dev.eolmae.marketry.domain.stock.entity.TradingSession;
import dev.eolmae.marketry.domain.stock.enums.MarketCalendarStatus;
import dev.eolmae.marketry.domain.stock.properties.MarketHoursProperties;
import dev.eolmae.marketry.domain.stock.repository.SectorPriceSnapshotRepository;
import dev.eolmae.marketry.domain.stock.repository.SectorPriceSnapshotRepositoryCustom.MarketSnapshotTime;
import dev.eolmae.marketry.domain.stock.repository.SectorPriceSnapshotRepositoryCustom.SnapshotDaySummary;
import dev.eolmae.marketry.domain.stock.repository.SectorPriceSnapshotRepositoryCustom.TimeWindow;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.YearMonth;
import java.time.ZoneOffset;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class SectorPriceSnapshotServiceTest {
    private static final LocalDate DATE = LocalDate.of(2025, 11, 13);
    private final SectorPriceSnapshotRepository repository = mock(SectorPriceSnapshotRepository.class);
    private final MarketCalendarService calendarService = mock(MarketCalendarService.class);
    private final MarketCalendarTimeService timeService = new MarketCalendarTimeService(
            calendarService, new MarketHoursProperties(LocalTime.of(15, 30), LocalTime.of(15, 40)));
    private final StockDailyPriceService dailyPrices = mock(StockDailyPriceService.class);
    private final SectorPriceSnapshotService service =
            new SectorPriceSnapshotService(repository, timeService, dailyPrices);

    /** 저장소가 조회 대상 날짜 중 일봉이 있는 날짜만 돌려주는 것처럼 흉내 낸다. */
    private void stubPriceDates(LocalDate... pricedDates) {
        when(dailyPrices.findKrxPriceDates(any())).thenAnswer(call -> {
            Collection<LocalDate> requested = call.getArgument(0);
            return requested.stream()
                    .filter(date -> List.of(pricedDates).contains(date))
                    .collect(java.util.stream.Collectors.toSet());
        });
    }

    private void calendar(int hour) {
        var offset = ZoneOffset.ofHours(9);
        var integrated = new IntegratedSessions(
                null,
                new TradingSession(
                        DATE.atTime(10, 0).atOffset(offset),
                        null,
                        null,
                        DATE.atTime(hour, 30).atOffset(offset)),
                new TradingSession(
                        DATE.atTime(hour, 30).atOffset(offset),
                        null,
                        DATE.atTime(hour, 40).atOffset(offset),
                        DATE.atTime(20, 0).atOffset(offset)));
        when(calendarService.findByCountryAndDateIn(eq(Country.KR), any()))
                .thenReturn(Map.of(
                        DATE, MarketCalendar.create(Country.KR, DATE, MarketCalendarStatus.TRADING_DAY, integrated)));
    }

    @Test
    void 한_시각만_있으면_새_시간표_경계_밖이라도_기존_값을_쓴다() {
        calendar(16);
        when(repository.findSnapshotDaySummaries(any()))
                .thenReturn(List.of(new SnapshotDaySummary(Market.KOSPI, DATE.atTime(17, 0), 1, null)));
        assertThat(service.findClosingSnapshotTimes(List.of(Market.KOSPI), YearMonth.from(DATE), DATE))
                .containsEntry(DATE, DATE.atTime(17, 0));
        service.cleanupSnapshotsForDate(DATE);
        verify(repository, never()).deleteSnapshotsForDate(any(), any());
    }

    @Test
    void 여러_시각이면_하한_없이_시간외_이전_latest를_조회하고_정리에_같이_쓴다() {
        calendar(16);
        when(repository.findSnapshotDaySummaries(any()))
                .thenReturn(List.of(new SnapshotDaySummary(Market.KOSPI, DATE.atTime(20, 0), 3, DATE.atTime(15, 35))));
        assertThat(service.findClosingSnapshotTimes(List.of(Market.KOSPI), YearMonth.from(DATE), DATE))
                .containsEntry(DATE, DATE.atTime(15, 35));
        ArgumentCaptor<List<TimeWindow>> windows = ArgumentCaptor.captor();
        verify(repository).findSnapshotDaySummaries(windows.capture());
        // 달력은 같은 달의 시간표 없는 평일도 거래일로 보므로 시간표가 있는 날짜의 구간만 확인한다.
        assertThat(windows.getValue()).contains(new TimeWindow(DATE.atStartOfDay(), DATE.atTime(16, 40)));
        service.cleanupSnapshotsForDate(DATE);
        verify(repository)
                .deleteSnapshotsForDate(DATE, List.of(new MarketSnapshotTime(Market.KOSPI, DATE.atTime(15, 35))));
    }

    @Test
    void 후보가_없는_날은_전날로_대체하지_않고_삭제도_보류한다() {
        calendar(15);
        when(repository.findSnapshotDaySummaries(any()))
                .thenReturn(List.of(new SnapshotDaySummary(Market.KOSPI, DATE.atTime(20, 0), 2, null)));
        assertThat(service.findClosingSnapshotTimes(List.of(Market.KOSPI), YearMonth.from(DATE), DATE))
                .isEmpty();
        service.cleanupSnapshotsForDate(DATE);
        verify(repository, never()).deleteSnapshotsForDate(any(), any());
    }

    @Test
    void 통합_달력은_두_시장에_공통_시각이_있는_날만_활성화한다() {
        calendar(15);
        when(repository.findSnapshotDaySummaries(any()))
                .thenReturn(List.of(
                        new SnapshotDaySummary(Market.KOSPI, DATE.atTime(15, 35), 1, null),
                        new SnapshotDaySummary(Market.KOSDAQ, DATE.atTime(15, 30), 1, null)));
        assertThat(service.findClosingSnapshotTimes(List.of(Market.KOSPI, Market.KOSDAQ), YearMonth.from(DATE), DATE))
                .isEmpty();
        assertThat(service.findClosingSnapshotTimes(List.of(Market.KOSPI), YearMonth.from(DATE), DATE))
                .containsEntry(DATE, DATE.atTime(15, 35));
    }

    @Test
    void 휴장은_일봉이_있어도_달력에서_제외하고_정리도_하지_않는다() {
        when(calendarService.findByCountryAndDateIn(eq(Country.KR), any()))
                .thenReturn(Map.of(DATE, MarketCalendar.create(Country.KR, DATE, MarketCalendarStatus.HOLIDAY, null)));
        stubPriceDates(DATE);

        assertThat(service.findClosingSnapshotTimes(List.of(Market.KOSPI), YearMonth.from(DATE), DATE))
                .isEmpty();
        ArgumentCaptor<Collection<LocalDate>> checkedDates = ArgumentCaptor.captor();
        verify(dailyPrices, org.mockito.Mockito.atMost(1)).findKrxPriceDates(checkedDates.capture());
        assertThat(checkedDates.getAllValues())
                .allSatisfy(dates -> assertThat(dates).doesNotContain(DATE));
        service.cleanupSnapshotsForDate(DATE);
        verify(repository, never()).deleteSnapshotsForDate(any(), any());
    }

    @Test
    void FAILED이거나_행이_없는_평일은_거래일로_보아_일봉이_있으면_활성화한다() {
        when(calendarService.findByCountryAndDateIn(eq(Country.KR), any()))
                .thenReturn(Map.of(DATE, MarketCalendar.create(Country.KR, DATE, MarketCalendarStatus.FAILED, null)));
        stubPriceDates(DATE);

        assertThat(service.findClosingSnapshotTimes(List.of(Market.KOSPI), YearMonth.from(DATE), DATE))
                .containsEntry(DATE, DATE.atTime(15, 40));
        when(calendarService.findByCountryAndDateIn(eq(Country.KR), any())).thenReturn(Map.of());
        assertThat(service.findClosingSnapshotTimes(List.of(Market.KOSPI), YearMonth.from(DATE), DATE))
                .containsEntry(DATE, DATE.atTime(15, 40));
    }

    @Test
    void 주말은_행이_없으면_일봉이_있어도_후보에서_제외한다() {
        LocalDate saturday = LocalDate.of(2025, 11, 15);
        service.findClosingSnapshotTimes(List.of(Market.KOSPI), YearMonth.from(DATE), saturday);

        ArgumentCaptor<Collection<LocalDate>> checkedDates = ArgumentCaptor.captor();
        verify(dailyPrices).findKrxPriceDates(checkedDates.capture());
        assertThat(checkedDates.getValue())
                .contains(DATE, saturday.minusDays(1))
                .doesNotContain(saturday, saturday.minusDays(6));
    }

    @Test
    void 정리는_확인된_거래일만_대상으로_하고_시간표_없는_날은_건너뛴다() {
        service.cleanupSnapshotsForDate(DATE);

        verifyNoInteractions(repository);
    }

    @Test
    void 일봉만_있는_날은_스냅샷_없이도_종가구간_끝_시각으로_활성화한다() {
        calendar(15);
        stubPriceDates(DATE);

        assertThat(service.findClosingSnapshotTimes(List.of(Market.KOSPI), YearMonth.from(DATE), DATE))
                .containsEntry(DATE, DATE.atTime(15, 40));
    }

    @Test
    void 스냅샷과_일봉이_모두_있으면_스냅샷_종가_시각을_쓴다() {
        calendar(15);
        when(repository.findSnapshotDaySummaries(any()))
                .thenReturn(List.of(new SnapshotDaySummary(Market.KOSPI, DATE.atTime(15, 35), 1, null)));
        stubPriceDates(DATE);

        assertThat(service.findClosingSnapshotTimes(List.of(Market.KOSPI), YearMonth.from(DATE), DATE))
                .containsEntry(DATE, DATE.atTime(15, 35));
    }

    @Test
    void 시장끼리_스냅샷_시각이_엇갈려도_일봉이_있으면_활성화한다() {
        calendar(15);
        when(repository.findSnapshotDaySummaries(any()))
                .thenReturn(List.of(
                        new SnapshotDaySummary(Market.KOSPI, DATE.atTime(15, 35), 1, null),
                        new SnapshotDaySummary(Market.KOSDAQ, DATE.atTime(15, 30), 1, null)));
        stubPriceDates(DATE);

        assertThat(service.findClosingSnapshotTimes(List.of(Market.KOSPI, Market.KOSDAQ), YearMonth.from(DATE), DATE))
                .containsEntry(DATE, DATE.atTime(15, 40));
    }

    @Test
    void 일봉도_스냅샷도_없는_거래일은_활성화하지_않는다() {
        calendar(15);

        assertThat(service.findClosingSnapshotTimes(List.of(Market.KOSPI), YearMonth.from(DATE), DATE))
                .isEmpty();
    }

    @Test
    void 토스가_미리_준_미래_날짜는_달력에서_제외한다() {
        assertThat(service.findClosingSnapshotTimes(List.of(Market.KOSPI), YearMonth.from(DATE), DATE.minusDays(1)))
                .isEmpty();
        ArgumentCaptor<Collection<LocalDate>> dates = ArgumentCaptor.captor();
        verify(calendarService).findByCountryAndDateIn(eq(Country.KR), dates.capture());
        assertThat(dates.getValue()).doesNotContain(DATE);
        assertThat(dates.getValue()).allMatch(date -> date.isBefore(DATE));
    }

    @Test
    void 최근_세_거래일은_시각_개수와_관계없이_센_뒤_이전의_미정리_날짜만_선택한다() {
        List<LocalDate> dates =
                List.of(DATE, DATE.minusDays(1), DATE.minusDays(2), DATE.minusDays(3), DATE.minusDays(6));
        when(repository.findTradingSnapshotDates(DATE)).thenReturn(dates);
        when(repository.findMultipleSnapshotDates(dates.get(3))).thenReturn(List.of(dates.get(4), dates.get(3)));
        assertThat(service.findCleanupDates(DATE, 3)).containsExactly(dates.get(4), dates.get(3));
    }

    @Test
    void 가격_있는_거래일이_세개_이하면_정리하지_않는다() {
        when(repository.findTradingSnapshotDates(DATE)).thenReturn(List.of(DATE, DATE.minusDays(1)));
        assertThat(service.findCleanupDates(DATE, 3)).isEmpty();
        verify(repository, never()).findMultipleSnapshotDates(any());
    }
}
