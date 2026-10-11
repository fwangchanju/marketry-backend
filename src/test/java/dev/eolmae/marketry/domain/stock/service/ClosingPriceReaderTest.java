package dev.eolmae.marketry.domain.stock.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class ClosingPriceReaderTest {
    private static final LocalDate TODAY = LocalDate.of(2026, 10, 9);
    private static final LocalDate YESTERDAY = TODAY.minusDays(1);
    private final ClosingPriceCacheService cache = mock(ClosingPriceCacheService.class);
    private final StockDailyPriceService dailyPrices = mock(StockDailyPriceService.class);
    private final MarketCalendarTimeService calendars = mock(MarketCalendarTimeService.class);
    private final ClosingPriceReader reader = new ClosingPriceReader(cache, dailyPrices, calendars);

    @Test
    void 일봉이_없으면_조회하는_스냅샷_날짜의_종가를_읽는다() {
        ClosingPrices prices = new ClosingPrices(TODAY, Map.of("005930", BigDecimal.TEN));
        when(dailyPrices.existsKrxPrice(TODAY)).thenReturn(false);
        when(cache.getFor(eq(TODAY), any())).thenReturn(prices);

        assertThat(reader.closingPricesFor(TODAY, TODAY)).isEqualTo(prices);
        verify(dailyPrices, never()).findKrxClosePrices(any());
    }

    @Test
    void 일봉이_있는_거래일은_스냅샷_대신_일봉_종가를_읽는다() {
        when(dailyPrices.existsKrxPrice(YESTERDAY)).thenReturn(true);
        when(calendars.isTradingDay(YESTERDAY)).thenReturn(true);
        when(dailyPrices.findKrxClosePrices(YESTERDAY)).thenReturn(Map.of("005930", BigDecimal.valueOf(100)));

        ClosingPrices prices = reader.closingPricesFor(YESTERDAY, TODAY);

        assertThat(prices.baseDate()).isEqualTo(YESTERDAY);
        assertThat(prices.priceByStockCode()).containsEntry("005930", BigDecimal.valueOf(100));
        verify(cache, never()).getFor(any(), any());
    }

    @Test
    void 명시적_휴장일은_일봉이_있어도_스냅샷_경로로_읽는다() {
        ClosingPrices empty = new ClosingPrices(YESTERDAY, Map.of());
        when(dailyPrices.existsKrxPrice(YESTERDAY)).thenReturn(true);
        when(calendars.isTradingDay(YESTERDAY)).thenReturn(false);
        when(cache.getFor(eq(YESTERDAY), any())).thenReturn(empty);

        assertThat(reader.hasOfficialClose(YESTERDAY)).isFalse();
        assertThat(reader.closingPricesFor(YESTERDAY, TODAY)).isEqualTo(empty);
    }

    @Test
    void 전일_종가는_연휴와_주말을_건너뛴_직전_거래일의_일봉_종가다() {
        LocalDate monday = LocalDate.of(2026, 10, 12);
        LocalDate lastTradingDay = LocalDate.of(2026, 10, 8);
        when(calendars.findPreviousTradingDay(monday)).thenReturn(Optional.of(lastTradingDay));
        when(dailyPrices.existsKrxPrice(lastTradingDay)).thenReturn(true);
        when(calendars.isTradingDay(lastTradingDay)).thenReturn(true);
        when(dailyPrices.findKrxClosePrices(lastTradingDay)).thenReturn(Map.of("005930", BigDecimal.valueOf(70)));

        ClosingPrices previous = reader.previousClosingPricesFor(monday);

        assertThat(previous.baseDate()).isEqualTo(lastTradingDay);
        assertThat(previous.priceByStockCode()).containsEntry("005930", BigDecimal.valueOf(70));
    }

    @Test
    void 직전_거래일의_일봉이_없으면_수집_실패와_무관하게_스냅샷_종가로_대체한다() {
        ClosingPrices snapshotClose = new ClosingPrices(YESTERDAY, Map.of("005930", BigDecimal.valueOf(65)));
        when(calendars.findPreviousTradingDay(TODAY)).thenReturn(Optional.of(YESTERDAY));
        when(dailyPrices.existsKrxPrice(YESTERDAY)).thenReturn(false);
        when(cache.getFor(eq(YESTERDAY), any())).thenReturn(snapshotClose);

        assertThat(reader.previousClosingPricesFor(TODAY)).isEqualTo(snapshotClose);
    }

    @Test
    void 직전_거래일을_찾을_수_없으면_빈_종가를_돌려준다() {
        when(calendars.findPreviousTradingDay(TODAY)).thenReturn(Optional.empty());

        assertThat(reader.previousClosingPricesFor(TODAY).priceByStockCode()).isEmpty();
    }
}
