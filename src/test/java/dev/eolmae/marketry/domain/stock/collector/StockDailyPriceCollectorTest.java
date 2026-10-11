package dev.eolmae.marketry.domain.stock.collector;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doCallRealMethod;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import dev.eolmae.marketry.common.enums.Country;
import dev.eolmae.marketry.common.enums.Market;
import dev.eolmae.marketry.common.enums.Zone;
import dev.eolmae.marketry.domain.stock.client.StockDailyPriceClient;
import dev.eolmae.marketry.domain.stock.entity.MarketCalendar;
import dev.eolmae.marketry.domain.stock.entity.StockDailyPrice;
import dev.eolmae.marketry.domain.stock.entity.StockInfo;
import dev.eolmae.marketry.domain.stock.enums.ExchangeType;
import dev.eolmae.marketry.domain.stock.enums.MarketCalendarStatus;
import dev.eolmae.marketry.domain.stock.properties.MarketHoursProperties;
import dev.eolmae.marketry.domain.stock.service.MarketCalendarService;
import dev.eolmae.marketry.domain.stock.service.MarketCalendarTimeService;
import dev.eolmae.marketry.domain.stock.service.StockDailyPriceService;
import dev.eolmae.marketry.domain.stock.service.StockInfoCacheService;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;

class StockDailyPriceCollectorTest {
    private static final LocalDate DATE = LocalDate.of(2026, 10, 8);
    private final StockDailyPriceClient client = mock(StockDailyPriceClient.class);
    private final StockDailyPriceService prices = mock(StockDailyPriceService.class);
    private final StockInfoCacheService stocks = mock(StockInfoCacheService.class);
    private final MarketCalendarTimeService calendars = mock(MarketCalendarTimeService.class);
    private final MutableClock clock = new MutableClock();
    private final List<Duration> waits = new ArrayList<>();
    private final List<List<StockDailyPrice>> savedPages = new ArrayList<>();
    private final ListAppender<ILoggingEvent> logs = new ListAppender<>();
    private final Logger collectorLogger = (Logger) LoggerFactory.getLogger(StockDailyPriceCollector.class);
    private final StockDailyPriceCollector collector =
            new StockDailyPriceCollector(client, prices, stocks, calendars, clock, duration -> {
                waits.add(duration);
                clock.now = clock.now.plus(duration);
            });

    @BeforeEach
    void startLogCapture() {
        logs.start();
        collectorLogger.addAppender(logs);
    }

    @AfterEach
    void stopLogCapture() {
        collectorLogger.detachAppender(logs);
    }

    @Test
    void 정상_빈응답은_재시도없이_저장없이_완료한다() {
        prepare(stock("005930", true));
        when(client.fetchFirstPage(anyString(), any(), any())).thenReturn(List.of());

        var result = collector.collect(DATE, List.of(), clock.now.plusSeconds(3000));

        assertThat(result.status()).isEqualTo(StockDailyPriceCollector.Status.COMPLETE);
        assertThat(result.attempts()).isEqualTo(2);
        assertThat(result.passes()).isEqualTo(1);
        verify(client, times(1)).fetchFirstPage("005930", ExchangeType.KRX, DATE);
        verify(client, times(1)).fetchFirstPage("005930", ExchangeType.NXT, DATE);
        verify(prices, never()).savePage(anyList());
    }

    @Test
    void 대상날짜_행이_없으면_다른날짜_행이_있어도_정상_skip이다() {
        prepare(stock("005930", false));
        when(client.fetchFirstPage(anyString(), any(), any())).thenReturn(List.of(row("20261007")));

        var result = collector.collect(DATE, List.of(), clock.now.plusSeconds(3000));

        assertThat(result.status()).isEqualTo(StockDailyPriceCollector.Status.COMPLETE);
        assertThat(result.attempts()).isEqualTo(1);
        verify(prices, never()).savePage(anyList());
    }

    @Test
    void API_실패한_종목거래소만_다음_회차에서_재요청하고_대상날짜_행만_저장한다() {
        prepare(stock("005930", true), stock("000660", false));
        when(client.fetchFirstPage(anyString(), any(), any())).thenReturn(List.of(row("20261008"), row("20261007")));
        when(client.fetchFirstPage("005930", ExchangeType.NXT, DATE))
                .thenThrow(new RuntimeException("temporary"))
                .thenReturn(List.of(row("20261008")));

        var result = collector.collect(DATE, List.of(), clock.now.plusSeconds(3000));

        assertThat(result.status()).isEqualTo(StockDailyPriceCollector.Status.COMPLETE);
        assertThat(result.passes()).isEqualTo(2);
        assertThat(result.attempts()).isEqualTo(4);
        verify(client, times(1)).fetchFirstPage("000660", ExchangeType.KRX, DATE);
        verify(client, times(1)).fetchFirstPage("005930", ExchangeType.KRX, DATE);
        verify(client, times(2)).fetchFirstPage("005930", ExchangeType.NXT, DATE);
        assertThat(savedPages).hasSize(3).allSatisfy(page -> {
            assertThat(page).hasSize(1);
            assertThat(page.getFirst().getTradeDate()).isEqualTo(DATE);
        });
        // 첫 회차는 200ms 간격으로 3건, 둘째 회차 요청은 500ms 간격이다.
        assertThat(waits).containsExactly(Duration.ofMillis(200), Duration.ofMillis(200), Duration.ofMillis(500));
    }

    @Test
    void 세회차_뒤에도_실패한_키만_남아_종목명과_함께_반환한다() {
        prepare(stock("005930", true));
        when(client.fetchFirstPage("005930", ExchangeType.KRX, DATE)).thenReturn(List.of(row("20261008")));
        when(client.fetchFirstPage("005930", ExchangeType.NXT, DATE)).thenThrow(new RuntimeException("temporary"));

        var result = collector.collect(DATE, List.of(), clock.now.plusSeconds(3000));

        assertThat(result.status()).isEqualTo(StockDailyPriceCollector.Status.INCOMPLETE);
        assertThat(result.failures())
                .containsExactly(new StockDailyPriceCollector.FailedStock("005930", "삼성전자", List.of(ExchangeType.NXT)));
        assertThat(result.passes()).isEqualTo(3);
        assertThat(result.attempts()).isEqualTo(4);
        verify(client, times(1)).fetchFirstPage("005930", ExchangeType.KRX, DATE);
        verify(client, times(3)).fetchFirstPage("005930", ExchangeType.NXT, DATE);
        assertThat(waits).containsExactly(Duration.ofMillis(200), Duration.ofMillis(500), Duration.ofSeconds(2));
    }

    @Test
    void 같은_종목의_KRX_NXT_실패는_한항목으로_묶는다() {
        prepare(stock("005930", true));
        when(client.fetchFirstPage(anyString(), any(), any())).thenThrow(new RuntimeException("temporary"));

        var result = collector.collect(DATE, List.of(), clock.now.plusSeconds(3000));

        assertThat(result.failures())
                .containsExactly(new StockDailyPriceCollector.FailedStock(
                        "005930", "삼성전자", List.of(ExchangeType.KRX, ExchangeType.NXT)));
    }

    @Test
    void 이미_적재된_행으로_저장이_skipped여도_실패목록에서_제거한다() {
        prepare(stock("005930", false));
        when(client.fetchFirstPage(anyString(), any(), any())).thenReturn(List.of(row("20261008")));
        when(prices.savePage(anyList())).thenReturn(new StockDailyPriceService.SaveResult(0, 1));

        var result = collector.collect(DATE, List.of(), clock.now.plusSeconds(3000));

        assertThat(result.status()).isEqualTo(StockDailyPriceCollector.Status.COMPLETE);
        assertThat(result.attempts()).isEqualTo(1);
        assertThat(result.inserted()).isZero();
    }

    @Test
    void 존재하는_날짜행의_숫자오류는_실패로_남겨_재시도한다() {
        prepare(stock("005930", false));
        var broken = new HashMap<>(row("20261008"));
        broken.put("cur_prc", "invalid");
        when(client.fetchFirstPage(anyString(), any(), any())).thenReturn(List.of(broken));

        var result = collector.collect(DATE, List.of(), clock.now.plusSeconds(3000));

        assertThat(result.status()).isEqualTo(StockDailyPriceCollector.Status.INCOMPLETE);
        assertThat(result.attempts()).isEqualTo(3);
        verify(prices, never()).savePage(anyList());
    }

    @Test
    void 저장_실패는_재시도하고_대상날짜의_원본_가격행만_로그로_남긴다() {
        prepare(stock("005930", false));
        when(client.fetchFirstPage(anyString(), any(), any())).thenReturn(List.of(row("20261008"), row("20261007")));
        when(prices.savePage(anyList())).thenThrow(new IllegalStateException("password=test-secret"));

        var result = collector.collect(DATE, List.of(), clock.now.plusSeconds(3000));

        assertThat(result.status()).isEqualTo(StockDailyPriceCollector.Status.INCOMPLETE);
        assertThat(result.attempts()).isEqualTo(3);
        var saveFailures = logs.list.stream()
                .map(ILoggingEvent::getFormattedMessage)
                .filter(message -> message.startsWith("[일봉 저장 실패]"))
                .toList();
        assertThat(saveFailures).hasSize(3);
        assertThat(saveFailures.getFirst())
                .contains("2026-10-08", "005930", "KRX", "open=100", "high=110", "low=90", "close=100", "volume=1000")
                .contains("IllegalStateException")
                .doesNotContain("test-secret", "2026-10-07");
    }

    @Test
    void 제한시간_이후에는_새_요청을_시작하지않고_DEADLINE을_반환한다() {
        prepare(stock("005930", true));

        var result = collector.collect(DATE, List.of(), clock.now);

        assertThat(result.status()).isEqualTo(StockDailyPriceCollector.Status.DEADLINE);
        assertThat(result.attempts()).isZero();
        assertThat(result.failures()).isEmpty();
        verifyNoInteractions(client);
    }

    @Test
    void 진행중_요청은_끝까지_기다리고_다음_요청부터_제한시간으로_중단한다() {
        prepare(stock("005930", true), stock("000660", true));
        Instant deadline = clock.now.plusMillis(100);
        // 첫 요청이 끝났을 때 이미 제한 시간을 넘긴 상황
        when(client.fetchFirstPage(anyString(), any(), any())).thenAnswer(call -> {
            clock.now = clock.now.plusSeconds(1);
            return List.of(row("20261008"));
        });

        var result = collector.collect(DATE, List.of(), deadline);

        assertThat(result.status()).isEqualTo(StockDailyPriceCollector.Status.DEADLINE);
        assertThat(result.attempts()).isEqualTo(1);
        verify(client, times(1)).fetchFirstPage(anyString(), any(), any());
        assertThat(savedPages).hasSize(1);
    }

    @Test
    void 휴장일이면_API없이_정상_종료한다() {
        when(calendars.isTradingDay(DATE)).thenReturn(false);

        var result = collector.collect(DATE, List.of(), clock.now.plusSeconds(3000));

        assertThat(result.status()).isEqualTo(StockDailyPriceCollector.Status.HOLIDAY);
        verifyNoInteractions(client, prices, stocks);
    }

    @Test
    void 캘린더가_FAILED이거나_없는_평일은_수집하고_토요일은_건너뛴다() {
        var calendarService = mock(MarketCalendarService.class);
        var realCalendars = new MarketCalendarTimeService(
                calendarService, new MarketHoursProperties(LocalTime.of(15, 30), LocalTime.of(15, 40)));
        var failedDate = LocalDate.of(2026, 10, 7);
        var saturday = LocalDate.of(2026, 10, 10);
        when(calendarService.findByCountryAndDate(Country.KR, failedDate))
                .thenReturn(
                        Optional.of(MarketCalendar.create(Country.KR, failedDate, MarketCalendarStatus.FAILED, null)));
        when(calendarService.findByCountryAndDate(Country.KR, DATE)).thenReturn(Optional.empty());
        when(calendarService.findByCountryAndDate(Country.KR, saturday)).thenReturn(Optional.empty());
        clock.now = LocalDate.of(2026, 10, 12)
                .atTime(5, 0)
                .atZone(Zone.KST.zoneId())
                .toInstant();
        when(stocks.getCache()).thenReturn(Map.of("005930", stock("005930", false)));
        when(client.fetchFirstPage(anyString(), any(), any())).thenReturn(List.of());
        var realCollector = new StockDailyPriceCollector(client, prices, stocks, realCalendars, clock, duration -> {});
        Instant deadline = clock.now.plusSeconds(3000);

        assertThat(realCollector.collect(failedDate, List.of(), deadline).status())
                .isEqualTo(StockDailyPriceCollector.Status.COMPLETE);
        assertThat(realCollector.collect(DATE, List.of(), deadline).status())
                .isEqualTo(StockDailyPriceCollector.Status.COMPLETE);
        verify(client, times(2)).fetchFirstPage(anyString(), any(), any());
        assertThat(realCollector.collect(saturday, List.of(), deadline).status())
                .isEqualTo(StockDailyPriceCollector.Status.HOLIDAY);
        verify(client, times(2)).fetchFirstPage(anyString(), any(), any());
    }

    private StockInfo stock(String code, boolean nxtEnabled) {
        String name = code.equals("005930") ? "삼성전자" : "SK하이닉스";
        return StockInfo.create(code, name, Market.KOSPI, "0", null, 100L, BigDecimal.TEN, nxtEnabled);
    }

    private void prepare(StockInfo... infos) {
        when(calendars.isTradingDay(DATE)).thenReturn(true);
        var cache = new HashMap<String, StockInfo>();
        for (StockInfo info : infos) {
            cache.put(info.getStockCode(), info);
        }
        when(stocks.getCache()).thenReturn(cache);
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
