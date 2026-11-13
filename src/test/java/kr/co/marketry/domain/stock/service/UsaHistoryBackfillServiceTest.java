package kr.co.marketry.domain.stock.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.io.ByteArrayInputStream;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import kr.co.marketry.common.exception.BadRequestException;
import kr.co.marketry.domain.stock.client.YahooFinanceQuoteClient;
import kr.co.marketry.domain.stock.client.YahooFinanceQuoteClient.DailyBar;
import kr.co.marketry.domain.stock.client.YahooFinanceQuoteClient.IndexSession;
import kr.co.marketry.domain.stock.client.YahooFinanceQuoteClient.MarketValue;
import kr.co.marketry.domain.stock.collector.UsaConstituents;
import kr.co.marketry.domain.stock.entity.HistoryBackfillProgress;
import kr.co.marketry.domain.stock.entity.MarketIndexDaily;
import kr.co.marketry.domain.stock.entity.OverseasStockQuote;
import kr.co.marketry.domain.stock.repository.HistoryBackfillProgressRepository;
import kr.co.marketry.domain.stock.repository.MarketIndexDailyRepository;
import kr.co.marketry.domain.stock.repository.OverseasStockQuoteRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class UsaHistoryBackfillServiceTest {
    private static final LocalDate DAY = LocalDate.of(2026, 11, 12);
    // 2026-11-12 16:00 EST = 21:00Z
    private static final Instant CLOSE = Instant.parse("2026-11-12T21:00:00Z");

    private YahooFinanceQuoteClient client;
    private OverseasStockQuoteRepository quotes;
    private MarketIndexDailyRepository indexes;
    private HistoryBackfillProgressRepository progress;

    @BeforeEach
    void setUp() {
        client = mock(YahooFinanceQuoteClient.class);
        quotes = mock(OverseasStockQuoteRepository.class);
        indexes = mock(MarketIndexDailyRepository.class);
        progress = mock(HistoryBackfillProgressRepository.class);
        when(client.fetchIndexSession("^GSPC")).thenReturn(new IndexSession(DAY, CLOSE));
    }

    @Test
    void 시작일_앞의_봉은_전일_종가로만_쓰고_첫_봉은_저장하지_않는다() {
        List<DailyBar> bars = List.of(bar("2019-12-26", "90"), bar("2019-12-27", "100"), bar("2019-12-30", "110"));

        List<OverseasStockQuote> rows = UsaHistoryBackfillService.toQuotes(
                "AAPL", bars, new BigDecimal("1100"), new BigDecimal("110"), LocalDate.of(2019, 12, 27));

        assertThat(rows).hasSize(2);
        assertThat(rows.getFirst().getId().getMarket()).isEqualTo("USA");
        assertThat(rows.getFirst().getLastPrice()).isEqualByComparingTo("90");
        assertThat(rows.getFirst().getChangeRate()).isEqualByComparingTo("11.11");
        assertThat(rows.getFirst().getId().getSnapshotTime().toLocalTime().toString())
                .isEqualTo("16:00");
        assertThat(rows.getLast().getTotalMarketValue()).isEqualByComparingTo("1100");
    }

    @Test
    void 시가총액은_최신값을_그날_종가_비율로_환산한다() {
        List<DailyBar> bars = List.of(bar("2026-11-10", "100"), bar("2026-11-11", "50"), bar("2026-11-12", "200"));

        List<OverseasStockQuote> rows = UsaHistoryBackfillService.toQuotes(
                "AAPL", bars, new BigDecimal("4000000"), new BigDecimal("200"), LocalDate.of(2026, 11, 10));

        assertThat(rows.getFirst().getTotalMarketValue()).isEqualByComparingTo("1000000");
        assertThat(rows.getLast().getTotalMarketValue()).isEqualByComparingTo("4000000");
    }

    @Test
    void 장이_끝나고_대기_시간이_지났으면_최신_봉_날짜까지_받는다() {
        UsaHistoryBackfillService service = service(CLOSE.plus(Duration.ofMinutes(30)), "AAPL");

        assertThat(service.completedDayAtOrBefore(LocalDate.of(2026, 12, 31))).isEqualTo(DAY);
        assertThat(service.completedDayAtOrBefore(LocalDate.of(2026, 11, 5))).isEqualTo(LocalDate.of(2026, 11, 5));
    }

    @Test
    void 장이_끝나기_전이거나_대기_시간_안이면_오늘_봉은_받지_않는다() {
        UsaHistoryBackfillService service = service(CLOSE.plus(Duration.ofMinutes(29)), "AAPL");

        assertThat(service.completedDayAtOrBefore(LocalDate.of(2026, 12, 31))).isEqualTo(DAY.minusDays(1));
    }

    @Test
    void 야후가_개장_전에_종료_시각을_오늘로_넘겨_놓아도_어제_종가까지_받는다() {
        // 2026-11-13 개장 전: 최신 일봉은 11/12, 종료 시각은 11/13 16:00 ET
        when(client.fetchIndexSession("^GSPC"))
                .thenReturn(new IndexSession(DAY, Instant.parse("2026-11-13T21:00:00Z")));
        UsaHistoryBackfillService service = service(Instant.parse("2026-11-13T14:00:00Z"), "AAPL");

        assertThat(service.completedDayAtOrBefore(LocalDate.of(2026, 11, 13))).isEqualTo(DAY);
    }

    @Test
    void 시작일이_종료일보다_늦으면_거절한다() {
        UsaHistoryBackfillService service = service(CLOSE.plus(Duration.ofHours(1)), "AAPL");

        assertThatThrownBy(() -> service.start(LocalDate.of(2026, 11, 10), LocalDate.of(2026, 11, 9)))
                .isInstanceOf(BadRequestException.class);
    }

    @Test
    void 받을_수_있는_종가가_시작일보다_앞이면_거절한다() {
        UsaHistoryBackfillService service = service(CLOSE.plus(Duration.ofHours(1)), "AAPL");

        assertThatThrownBy(() -> service.start(LocalDate.of(2026, 11, 20), LocalDate.of(2026, 11, 30)))
                .isInstanceOf(BadRequestException.class);
        verify(client, times(1)).fetchIndexSession("^GSPC");
    }

    @Test
    void 종목마다_일봉을_저장하고_끝난_항목을_기록한다() {
        UsaHistoryBackfillService service = service(CLOSE.plus(Duration.ofHours(1)), "AAPL", "MSFT");
        givenBars("AAPL");
        givenBars("MSFT");
        when(client.fetchDailyBars(eq("^GSPC"), any(), any()))
                .thenReturn(List.of(bar("2026-11-10", "6000"), bar("2026-11-11", "6060")));

        service.run(LocalDate.of(2026, 11, 10), DAY);

        assertThat(service.status().running()).isFalse();
        assertThat(service.status().done()).isEqualTo(3);
        assertThat(service.status().savedRows()).isEqualTo(6);
        verify(quotes, times(2)).saveAll(any());
        ArgumentCaptor<HistoryBackfillProgress> captor = ArgumentCaptor.forClass(HistoryBackfillProgress.class);
        verify(progress, times(3)).save(captor.capture());
        assertThat(captor.getAllValues().getFirst().getId().getJob()).isEqualTo("USA:2026-11-10");
    }

    @Test
    void 지수는_시가_고가_저가_종가를_그날짜로_저장한다() {
        UsaHistoryBackfillService service = service(CLOSE.plus(Duration.ofHours(1)), "AAPL");
        givenBars("AAPL");
        when(client.fetchDailyBars(eq("^GSPC"), any(), any()))
                .thenReturn(List.of(bar("2026-11-10", "6000"), bar("2026-11-11", "6060")));

        service.run(LocalDate.of(2026, 11, 10), DAY);

        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<MarketIndexDaily>> captor = ArgumentCaptor.forClass(List.class);
        verify(indexes).saveAll(captor.capture());
        assertThat(captor.getValue()).hasSize(2);
        assertThat(captor.getValue().getLast().getCloseValue()).isEqualByComparingTo("6060");
        assertThat(captor.getValue().getLast().getId().getMarket()).isEqualTo("SP500");
        assertThat(captor.getValue().getLast().getId().getTradeDate()).isEqualTo(LocalDate.of(2026, 11, 11));
    }

    @Test
    void 시가총액은_일봉과_같은_시작일부터_찾아서_오래전에_멈춘_기록도_놓치지_않는다() {
        UsaHistoryBackfillService service = service(CLOSE.plus(Duration.ofHours(1)), "META");
        when(client.fetchDailyBars(eq("META"), any(), any()))
                .thenReturn(List.of(bar("2025-10-01", "700"), bar("2025-10-02", "710"), bar("2026-11-11", "600")));
        // 야후 시가총액 기록이 2025-10-02에서 멈춘 종목
        when(client.fetchMarketValueOf(eq("META"), any(), any()))
                .thenReturn(new MarketValue(LocalDate.of(2025, 10, 2), new BigDecimal("1800000")));

        service.run(LocalDate.of(2019, 12, 27), DAY);

        ArgumentCaptor<LocalDate> fromCaptor = ArgumentCaptor.forClass(LocalDate.class);
        verify(client).fetchMarketValueOf(eq("META"), fromCaptor.capture(), any());
        assertThat(fromCaptor.getValue()).isEqualTo(LocalDate.of(2019, 12, 13));
        assertThat(service.status().failed()).isZero();
    }

    @Test
    void 이미_끝난_항목은_호출하지_않고_건너뛴다() {
        UsaHistoryBackfillService service = service(CLOSE.plus(Duration.ofHours(1)), "AAPL", "MSFT");
        when(progress.existsById(new HistoryBackfillProgress.Id("USA:2026-11-10", "AAPL")))
                .thenReturn(true);
        givenBars("MSFT");

        service.run(LocalDate.of(2026, 11, 10), DAY);

        assertThat(service.status().skipped()).isEqualTo(1);
        assertThat(service.status().done()).isEqualTo(2);
        verify(client, never()).fetchDailyBars(eq("AAPL"), any(), any());
    }

    @Test
    void 한_종목이_실패해도_다음_종목을_이어서_받는다() {
        UsaHistoryBackfillService service = service(CLOSE.plus(Duration.ofHours(1)), "AAPL", "MSFT");
        when(client.fetchDailyBars(eq("AAPL"), any(), any())).thenThrow(new IllegalStateException("일봉 없음"));
        givenBars("MSFT");

        service.run(LocalDate.of(2026, 11, 10), DAY);

        assertThat(service.status().failed()).isEqualTo(1);
        assertThat(service.status().done()).isEqualTo(2);
        assertThat(service.status().stopReason()).isNull();
    }

    @Test
    void 연속으로_스무_번_실패하면_멈춘다() {
        String[] symbols = new String[25];
        for (int index = 0; index < symbols.length; index++) {
            symbols[index] = "S" + index;
        }
        UsaHistoryBackfillService service = service(CLOSE.plus(Duration.ofHours(1)), symbols);
        when(client.fetchDailyBars(anyString(), any(), any())).thenThrow(new IllegalStateException("막힘"));

        service.run(LocalDate.of(2026, 11, 10), DAY);

        assertThat(service.status().failed()).isEqualTo(20);
        assertThat(service.status().stopReason()).isEqualTo("연속 실패 20회로 멈춤");
    }

    private void givenBars(String symbol) {
        when(client.fetchDailyBars(eq(symbol), any(), any()))
                .thenReturn(List.of(bar("2026-11-09", "100"), bar("2026-11-10", "101"), bar("2026-11-11", "102")));
        when(client.fetchMarketValueOf(eq(symbol), any(), any()))
                .thenReturn(new MarketValue(LocalDate.of(2026, 11, 11), new BigDecimal("1020")));
    }

    private UsaHistoryBackfillService service(Instant now, String... symbols) {
        StringBuilder csv = new StringBuilder("symbol,name,date_added\n");
        for (String symbol : symbols) {
            csv.append(symbol).append(",").append(symbol).append(" Inc,2020-01-01\n");
        }
        UsaConstituents constituents =
                new UsaConstituents(new ByteArrayInputStream(csv.toString().getBytes(StandardCharsets.UTF_8)));
        return new UsaHistoryBackfillService(
                client, quotes, indexes, progress, constituents, Clock.fixed(now, ZoneOffset.UTC)) {
            @Override
            void pause(Duration duration) {}
        };
    }

    private static DailyBar bar(String date, String close) {
        BigDecimal price = new BigDecimal(close);
        return new DailyBar(LocalDate.parse(date), price, price, price, price, 1);
    }
}
