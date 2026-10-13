package dev.eolmae.marketry.domain.stock.collector;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import dev.eolmae.marketry.common.enums.Market;
import dev.eolmae.marketry.domain.stock.client.TossStockListClient;
import dev.eolmae.marketry.domain.stock.entity.StockInfo;
import dev.eolmae.marketry.domain.stock.service.MarketCalendarTimeService;
import dev.eolmae.marketry.domain.stock.service.StockInfoCacheService;
import dev.eolmae.marketry.domain.stock.service.StockTradingSuspensionService;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Collection;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;

class StockSuspensionCollectorTest {
    private final StockInfoCacheService stockInfoCacheService = Mockito.mock(StockInfoCacheService.class);
    private final TossStockListClient tossStockListClient = Mockito.mock(TossStockListClient.class);
    private final StockTradingSuspensionService suspensionService = Mockito.mock(StockTradingSuspensionService.class);
    private final MarketCalendarTimeService calendar = Mockito.mock(MarketCalendarTimeService.class);
    private final StockSuspensionCollector collector =
            new StockSuspensionCollector(stockInfoCacheService, tossStockListClient, suspensionService, calendar);

    @Test
    void 휴장일에는_토스를_부르지_않고_기록하지_않는다() {
        when(calendar.isTradingDay(any(LocalDate.class))).thenReturn(false);

        collector.sync();

        verify(tossStockListClient, never()).fetchSuspendedStockCodes(anyCollection());
        verify(suspensionService, never()).record(any(), any());
    }

    @Test
    void 거래일에는_활성_보통주만_조회해_거래정지_종목을_기록한다() {
        StockInfo ordinary = StockInfo.create("005930", "삼성전자", Market.KOSPI, "0", null, 100L, BigDecimal.TEN, false);
        StockInfo etf = StockInfo.create("069500", "ETF", Market.KOSPI, "8", null, 100L, BigDecimal.TEN, false);
        StockInfo inactive = StockInfo.create("999999", "비활성", Market.KOSPI, "0", null, 100L, BigDecimal.TEN, false);
        inactive.markInactive();
        when(calendar.isTradingDay(any(LocalDate.class))).thenReturn(true);
        when(stockInfoCacheService.getCache())
                .thenReturn(Map.of("005930", ordinary, "069500", etf, "999999", inactive));
        when(tossStockListClient.fetchSuspendedStockCodes(anyCollection())).thenReturn(Set.of("005930"));

        collector.sync();

        @SuppressWarnings("unchecked")
        ArgumentCaptor<Collection<String>> codes = ArgumentCaptor.forClass(Collection.class);
        verify(tossStockListClient).fetchSuspendedStockCodes(codes.capture());
        org.assertj.core.api.Assertions.assertThat(codes.getValue()).containsExactly("005930");
        verify(suspensionService).record(Mockito.eq(Set.of("005930")), any(LocalDate.class));
    }
}
