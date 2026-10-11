package dev.eolmae.marketry.domain.stock.collector;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doCallRealMethod;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import dev.eolmae.marketry.common.enums.Country;
import dev.eolmae.marketry.common.enums.Market;
import dev.eolmae.marketry.common.enums.Zone;
import dev.eolmae.marketry.domain.stock.client.StockDailyPriceClient;
import dev.eolmae.marketry.domain.stock.dto.MissingDailyPriceStock;
import dev.eolmae.marketry.domain.stock.entity.MarketCalendar;
import dev.eolmae.marketry.domain.stock.entity.StockDailyPrice;
import dev.eolmae.marketry.domain.stock.entity.StockInfo;
import dev.eolmae.marketry.domain.stock.enums.ExchangeType;
import dev.eolmae.marketry.domain.stock.enums.MarketCalendarStatus;
import dev.eolmae.marketry.domain.stock.service.MarketCalendarService;
import dev.eolmae.marketry.domain.stock.service.StockDailyPriceService;
import dev.eolmae.marketry.domain.stock.service.StockInfoCacheService;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class StockDailyPriceCollectorTest {
    private static final LocalDate DATE = LocalDate.of(2026, 10, 8);
    private final StockDailyPriceClient client = mock(StockDailyPriceClient.class);
    private final StockDailyPriceService prices = mock(StockDailyPriceService.class);
    private final StockInfoCacheService stocks = mock(StockInfoCacheService.class);
    private final MarketCalendarService calendars = mock(MarketCalendarService.class);
    private final MutableClock clock = new MutableClock();
    private final List<Duration> waits = new ArrayList<>();
    private final List<List<StockDailyPrice>> savedPages = new ArrayList<>();
    private final StockDailyPriceCollector collector =
            new StockDailyPriceCollector(client, prices, stocks, calendars, clock, duration -> {
                waits.add(duration);
                clock.now = clock.now.plus(duration);
            });
    private final MissingDailyPriceStock both = new MissingDailyPriceStock("005930", "삼성전자", true, true);
    private final MissingDailyPriceStock nxtOnly = new MissingDailyPriceStock("005930", "삼성전자", false, true);

    @Test
    void 하루만_저장하고_실패한_NXT만_재요청한다() {
        prepare();
        when(prices.findMissing(DATE, List.of("005930")))
                .thenReturn(List.of(both))
                .thenReturn(List.of(nxtOnly))
                .thenReturn(List.of());
        when(client.fetchFirstPage(anyString(), any(), any(), any()))
                .thenReturn(List.of(row("20261008"), row("20261007")));
        when(client.fetchFirstPage(anyString(), org.mockito.ArgumentMatchers.eq(ExchangeType.NXT), any(), any()))
                .thenThrow(new RuntimeException("temporary"))
                .thenReturn(List.of(row("20261008")));
        var result = collector.collect(DATE, List.of(), clock.now.plusSeconds(3000));
        assertThat(result.status()).isEqualTo(StockDailyPriceCollector.Status.COMPLETE);
        assertThat(result.attempts()).isEqualTo(3);
        assertThat(result.passes()).isEqualTo(2);
        verify(client, times(1))
                .fetchFirstPage(anyString(), org.mockito.ArgumentMatchers.eq(ExchangeType.KRX), any(), any());
        verify(client, times(2))
                .fetchFirstPage(anyString(), org.mockito.ArgumentMatchers.eq(ExchangeType.NXT), any(), any());
        assertThat(savedPages).hasSize(2).allSatisfy(page -> {
            assertThat(page).hasSize(1);
            assertThat(page.getFirst().getTradeDate()).isEqualTo(DATE);
        });
        assertThat(waits).containsExactly(Duration.ofMillis(200), Duration.ofMillis(500));
    }

    @Test
    void 세번_실패하면_2초간격까지_시도하고_누락을_반환한다() {
        prepare();
        when(prices.findMissing(DATE, List.of("005930"))).thenReturn(List.of(nxtOnly));
        when(client.fetchFirstPage(anyString(), any(), any(), any())).thenThrow(new RuntimeException("temporary"));
        var result = collector.collect(DATE, List.of(), clock.now.plusSeconds(3000));
        assertThat(result.status()).isEqualTo(StockDailyPriceCollector.Status.INCOMPLETE);
        assertThat(result.missing()).containsExactly(nxtOnly);
        assertThat(result.attempts()).isEqualTo(3);
        assertThat(waits).containsExactly(Duration.ofMillis(500), Duration.ofSeconds(2));
    }

    @Test
    void 빈응답과_다른날짜는_누락을_해결하지못한다() {
        prepare();
        when(prices.findMissing(DATE, List.of("005930"))).thenReturn(List.of(nxtOnly));
        when(client.fetchFirstPage(anyString(), any(), any(), any())).thenReturn(List.of(row("20261007")));
        when(prices.savePage(List.of())).thenReturn(new StockDailyPriceService.SaveResult(0, 0));
        var result = collector.collect(DATE, List.of(), clock.now.plusSeconds(3000));
        assertThat(result.status()).isEqualTo(StockDailyPriceCollector.Status.INCOMPLETE);
        assertThat(result.inserted()).isZero();
    }

    @Test
    void 기한에_처리여유가_없으면_API를_시작하지않는다() {
        prepare();
        when(prices.findMissing(DATE, List.of("005930"))).thenReturn(List.of(both));
        var result = collector.collect(DATE, List.of(), clock.now.plusSeconds(40));
        assertThat(result.status()).isEqualTo(StockDailyPriceCollector.Status.DEADLINE);
        assertThat(result.attempts()).isZero();
        assertThat(result.missing()).containsExactly(both);
        verifyNoInteractions(client);
    }

    @Test
    void 휴장_FAILED_시간표없음에서는_API를_호출하지않는다() {
        when(calendars.findByCountryAndDate(Country.KR, DATE))
                .thenReturn(Optional.of(MarketCalendar.create(Country.KR, DATE, MarketCalendarStatus.HOLIDAY, null)))
                .thenReturn(Optional.of(MarketCalendar.create(Country.KR, DATE, MarketCalendarStatus.FAILED, null)))
                .thenReturn(Optional.empty());
        assertThat(collector
                        .collect(DATE, List.of(), clock.now.plusSeconds(3000))
                        .status())
                .isEqualTo(StockDailyPriceCollector.Status.HOLIDAY);
        assertThat(collector
                        .collect(DATE, List.of(), clock.now.plusSeconds(3000))
                        .status())
                .isEqualTo(StockDailyPriceCollector.Status.CALENDAR_UNAVAILABLE);
        assertThat(collector
                        .collect(DATE, List.of(), clock.now.plusSeconds(3000))
                        .status())
                .isEqualTo(StockDailyPriceCollector.Status.CALENDAR_UNAVAILABLE);
        verifyNoInteractions(client, prices, stocks);
    }

    private void prepare() {
        when(calendars.findByCountryAndDate(Country.KR, DATE))
                .thenReturn(
                        Optional.of(MarketCalendar.create(Country.KR, DATE, MarketCalendarStatus.TRADING_DAY, null)));
        when(stocks.getCache())
                .thenReturn(Map.of(
                        "005930",
                        StockInfo.create("005930", "삼성전자", Market.KOSPI, "0", null, 100L, BigDecimal.TEN, true)));
        doCallRealMethod().when(client).parsePrices(anyString(), any(), anyList(), any());
        when(prices.savePage(anyList())).thenAnswer(call -> {
            List<StockDailyPrice> page = call.getArgument(0);
            savedPages.add(page);
            return new StockDailyPriceService.SaveResult(page.size(), 0);
        });
    }

    private Map<String, Object> row(String date) {
        return Map.of(
                "dt",
                date,
                "open_pric",
                "100",
                "high_pric",
                "110",
                "low_pric",
                "90",
                "cur_prc",
                "100",
                "trde_qty",
                "1000");
    }

    private static final class MutableClock extends Clock {
        private Instant now =
                LocalDate.of(2026, 10, 9).atTime(5, 0).atZone(Zone.KST.zoneId()).toInstant();

        @Override
        public ZoneId getZone() {
            return Zone.KST.zoneId();
        }

        @Override
        public Clock withZone(ZoneId zone) {
            return this;
        }

        @Override
        public Instant instant() {
            return now;
        }
    }
}
