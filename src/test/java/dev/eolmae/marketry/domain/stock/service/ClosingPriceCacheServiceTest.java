package dev.eolmae.marketry.domain.stock.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import dev.eolmae.marketry.common.enums.Country;
import dev.eolmae.marketry.common.enums.Market;
import dev.eolmae.marketry.common.event.MarketCalendarChangedEvent;
import dev.eolmae.marketry.domain.stock.entity.SectorPriceSnapshot;
import dev.eolmae.marketry.domain.stock.enums.ExchangeType;
import dev.eolmae.marketry.domain.stock.repository.SectorPriceSnapshotRepository;
import dev.eolmae.marketry.domain.stock.repository.SectorPriceSnapshotRepositoryCustom.MarketSnapshotTime;
import dev.eolmae.marketry.domain.stock.repository.SectorPriceSnapshotRepositoryCustom.SnapshotDaySummary;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class ClosingPriceCacheServiceTest {
    private static final LocalDate DATE = LocalDate.of(2026, 10, 2);
    private final SectorPriceSnapshotRepository repository = mock(SectorPriceSnapshotRepository.class);
    private final MarketCalendarTimeService timeService = mock(MarketCalendarTimeService.class);
    private final ClosingPriceCacheService service = new ClosingPriceCacheService(
            repository,
            timeService,
            new SectorPriceSnapshotService(repository, timeService, mock(StockDailyPriceService.class)));

    @BeforeEach
    void 기본_경계() {
        when(timeService.resolve(DATE)).thenReturn(times(15));
    }

    private CalendarDayTimes times(int hour) {
        return new CalendarDayTimes(
                false,
                DATE.atTime(8, 0),
                DATE.atTime(20, 0),
                DATE.atTime(hour, 30),
                DATE.atTime(hour, 30),
                DATE.atTime(hour, 40));
    }

    private SectorPriceSnapshot snapshot(String code, Market market, LocalDateTime time, String price) {
        return SectorPriceSnapshot.create(
                market, time, code, ExchangeType.SOR, code, new BigDecimal(price), BigDecimal.ZERO, BigDecimal.ZERO);
    }

    private void stub(int hour, String price) {
        LocalDateTime selected = DATE.atTime(hour, 35);
        when(repository.findSnapshotDaySummaries(any()))
                .thenReturn(List.of(new SnapshotDaySummary(Market.KOSPI, selected, 1, null)));
        when(repository.findByMarketSnapshotTimes(any()))
                .thenReturn(List.of(snapshot("005930", Market.KOSPI, selected, price)));
    }

    @Test
    void 이미_정리된_예전_종가는_시간표가_늦어졌어도_읽는다() {
        when(timeService.resolve(DATE)).thenReturn(times(16));
        stub(15, "100");
        assertThat(service.loadFor(DATE).priceByStockCode()).containsEntry("005930", new BigDecimal("100"));
    }

    @Test
    void 시장별_종가_시각이_달라도_각각의_종목_가격을_읽는다() {
        LocalDateTime kospi = DATE.atTime(15, 35);
        LocalDateTime kosdaq = DATE.atTime(15, 30);
        when(repository.findSnapshotDaySummaries(any()))
                .thenReturn(List.of(
                        new SnapshotDaySummary(Market.KOSPI, DATE.atTime(20, 0), 3, kospi),
                        new SnapshotDaySummary(Market.KOSDAQ, kosdaq, 1, null)));
        when(repository.findByMarketSnapshotTimes(any()))
                .thenReturn(List.of(
                        snapshot("005930", Market.KOSPI, kospi, "100"),
                        snapshot("035900", Market.KOSDAQ, kosdaq, "200")));
        assertThat(service.loadFor(DATE).priceByStockCode())
                .containsEntry("005930", new BigDecimal("100"))
                .containsEntry("035900", new BigDecimal("200"));
        verify(repository)
                .findByMarketSnapshotTimes(List.of(
                        new MarketSnapshotTime(Market.KOSPI, kospi), new MarketSnapshotTime(Market.KOSDAQ, kosdaq)));
    }

    @Test
    void 시간외_시작_전에는_새로운_latest를_계속_읽는다() {
        stub(15, "100");
        assertThat(service.getFor(DATE, DATE.atTime(15, 36)).priceByStockCode())
                .containsEntry("005930", new BigDecimal("100"));
        stub(15, "110");
        assertThat(service.getFor(DATE, DATE.atTime(15, 39)).priceByStockCode())
                .containsEntry("005930", new BigDecimal("110"));
    }

    @Test
    void 시간외_시작부터_캐시하고_시간표_변경_이벤트로_비운다() {
        stub(15, "100");
        service.getFor(DATE, DATE.atTime(15, 40));
        stub(15, "110");
        assertThat(service.getFor(DATE, DATE.atTime(18, 0)).priceByStockCode())
                .containsEntry("005930", new BigDecimal("100"));
        service.onMarketCalendarChanged(new MarketCalendarChangedEvent(Country.KR, DATE));
        assertThat(service.getFor(DATE, DATE.atTime(18, 0)).priceByStockCode())
                .containsEntry("005930", new BigDecimal("110"));
    }

    @Test
    void 이벤트_없이_시간표_경계가_바뀌어도_캐시를_다시_계산한다() {
        stub(15, "100");
        service.getFor(DATE, DATE.atTime(18, 0));
        when(timeService.resolve(DATE)).thenReturn(times(16));
        stub(16, "150");
        assertThat(service.getFor(DATE, DATE.atTime(18, 0)).priceByStockCode())
                .containsEntry("005930", new BigDecimal("150"));
    }

    @Test
    void 빈_종가는_그_날짜를_유지하고_캐시하지_않는다() {
        ClosingPrices empty = service.getFor(DATE, DATE.atTime(18, 0));
        assertThat(empty.baseDate()).isEqualTo(DATE);
        assertThat(empty.priceByStockCode()).isEmpty();
        stub(15, "100");
        assertThat(service.getFor(DATE, DATE.atTime(18, 0)).priceByStockCode())
                .containsEntry("005930", new BigDecimal("100"));
    }

    @Test
    void 휴장일_가격은_읽지_않는다() {
        when(timeService.resolve(DATE)).thenReturn(new CalendarDayTimes(true, null, null, null, null, null));
        assertThat(service.getFor(DATE, DATE.atTime(18, 0)).priceByStockCode()).isEmpty();
        verifyNoInteractions(repository);
    }
}
