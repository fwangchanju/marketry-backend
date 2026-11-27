package kr.co.marketry.domain.stock.collector;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.io.ByteArrayInputStream;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import kr.co.marketry.domain.stock.client.YahooFinanceQuoteClient;
import kr.co.marketry.domain.stock.client.YahooFinanceQuoteClient.DailyBar;
import kr.co.marketry.domain.stock.entity.OverseasStockDaily;
import kr.co.marketry.domain.stock.entity.OverseasStockQuote;
import kr.co.marketry.domain.stock.entity.OverseasStockQuoteId;
import kr.co.marketry.domain.stock.repository.OverseasStockDailyRepository;
import kr.co.marketry.domain.stock.repository.OverseasStockQuoteRepository;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.http.HttpStatus;
import org.springframework.web.client.HttpClientErrorException;

class UsaCloseReconcilerTest {
    private static final LocalDate DAY = LocalDate.of(2026, 11, 27);
    private static final LocalDate PREVIOUS_DAY = LocalDate.of(2026, 11, 25);
    private static final LocalDateTime CLOSE = DAY.atTime(16, 0);

    private final YahooFinanceQuoteClient client = mock(YahooFinanceQuoteClient.class);
    private final OverseasStockQuoteRepository repository = mock(OverseasStockQuoteRepository.class);
    private final OverseasStockDailyRepository dailies = mock(OverseasStockDailyRepository.class);

    private UsaCloseReconciler reconciler(String... symbols) {
        StringBuilder csv = new StringBuilder("symbol,name,date_added\n");
        for (String symbol : symbols) {
            csv.append(symbol).append(",").append(symbol).append(" Inc,2020-01-01\n");
        }
        UsaConstituents constituents =
                new UsaConstituents(new ByteArrayInputStream(csv.toString().getBytes(StandardCharsets.UTF_8)));
        return new UsaCloseReconciler(client, repository, dailies, constituents, duration -> {});
    }

    private static OverseasStockQuote row(
            String symbol, LocalDateTime time, String price, String cap, boolean verified) {
        OverseasStockQuote quote = OverseasStockQuote.create(
                new OverseasStockQuoteId("USA", symbol, time),
                new BigDecimal(price),
                new BigDecimal(price),
                BigDecimal.ZERO,
                new BigDecimal(cap));
        if (verified) {
            quote.markCloseVerified();
        }
        return quote;
    }

    private static DailyBar bar(LocalDate day, String close) {
        BigDecimal price = new BigDecimal(close);
        return new DailyBar(day, price, price, price, price, 1000);
    }

    @SuppressWarnings("unchecked")
    private List<OverseasStockQuote> savedRows() {
        ArgumentCaptor<Iterable<OverseasStockQuote>> captor = ArgumentCaptor.forClass(Iterable.class);
        verify(repository).saveAll(captor.capture());
        return (List<OverseasStockQuote>) (List<?>)
                java.util.stream.StreamSupport.stream(captor.getValue().spliterator(), false)
                        .toList();
    }

    @Test
    void 종가_줄이_없는_날은_아무것도_하지_않는다() {
        when(repository.findAllByIdMarketAndIdSnapshotTime("USA", CLOSE)).thenReturn(List.of());

        UsaCloseReconciler.Result result = reconciler("AAPL").reconcile(DAY);

        assertThat(result.saved()).isZero();
        assertThat(result.failures()).isEmpty();
        verify(client, never()).fetchDailyBars(any(), any(), any());
        verify(repository, never()).saveAll(any());
    }

    @Test
    void 이미_모두_확인된_종가_줄은_건너뛴다() {
        when(repository.findAllByIdMarketAndIdSnapshotTime("USA", CLOSE))
                .thenReturn(List.of(row("AAPL", CLOSE, "110.00", "1100000", true)));

        UsaCloseReconciler.Result result = reconciler("AAPL").reconcile(DAY);

        assertThat(result.saved()).isZero();
        verify(client, never()).fetchDailyBars(any(), any(), any());
    }

    @Test
    void 확정_일봉_종가와_전일_종가로_바꾸고_확인_표시를_한다() {
        when(repository.findAllByIdMarketAndIdSnapshotTime("USA", CLOSE))
                .thenReturn(List.of(row("AAPL", CLOSE, "110.00", "1100000", false)));
        when(client.fetchDailyBars(eq("AAPL"), any(), eq(DAY)))
                .thenReturn(List.of(bar(PREVIOUS_DAY, "100.00"), bar(DAY, "111.00")));

        UsaCloseReconciler.Result result = reconciler("AAPL").reconcile(DAY);

        assertThat(result.saved()).isEqualTo(1);
        assertThat(result.failures()).isEmpty();
        OverseasStockQuote saved = savedRows().getFirst();
        assertThat(saved.getId().getSnapshotTime()).isEqualTo(CLOSE);
        assertThat(saved.getCurrentPrice()).isEqualByComparingTo("111.00");
        assertThat(saved.getLastPrice()).isEqualByComparingTo("100.00");
        assertThat(saved.getChangeRate()).isEqualByComparingTo("11.00");
        assertThat(saved.getTotalMarketValue()).isEqualByComparingTo("1110000");
        assertThat(saved.getCloseVerified()).isTrue();
        verify(dailies).saveAll(any());
    }

    @Test
    void 저장_때_빠진_종목은_앞선_종가_줄의_시가총액에_이어_붙여_채운다() {
        when(repository.findAllByIdMarketAndIdSnapshotTime("USA", CLOSE))
                .thenReturn(List.of(row("AAPL", CLOSE, "110.00", "1100000", true)));
        when(repository.findSnapshotTimes("USA"))
                .thenReturn(List.of(PREVIOUS_DAY.atTime(16, 0), PREVIOUS_DAY.atTime(15, 0), CLOSE));
        when(repository.findAllByIdMarketAndIdSnapshotTime("USA", PREVIOUS_DAY.atTime(16, 0)))
                .thenReturn(List.of(row("HUBB", PREVIOUS_DAY.atTime(16, 0), "400.00", "2000000", true)));
        when(client.fetchDailyBars(eq("HUBB"), any(), eq(DAY)))
                .thenReturn(List.of(bar(PREVIOUS_DAY, "400.00"), bar(DAY, "440.00")));

        UsaCloseReconciler.Result result = reconciler("AAPL", "HUBB").reconcile(DAY);

        assertThat(result.saved()).isEqualTo(1);
        OverseasStockQuote saved = savedRows().getFirst();
        assertThat(saved.getId().getStockCode()).isEqualTo("HUBB");
        assertThat(saved.getCurrentPrice()).isEqualByComparingTo("440.00");
        assertThat(saved.getTotalMarketValue()).isEqualByComparingTo("2200000");
    }

    @Test
    void 끝까지_못_받은_종목은_실패로_남기고_나머지는_보정한다() {
        when(repository.findAllByIdMarketAndIdSnapshotTime("USA", CLOSE))
                .thenReturn(List.of(
                        row("AAPL", CLOSE, "110.00", "1100000", false),
                        row("MSFT", CLOSE, "200.00", "2000000", false)));
        when(client.fetchDailyBars(eq("AAPL"), any(), eq(DAY)))
                .thenReturn(List.of(bar(PREVIOUS_DAY, "100.00"), bar(DAY, "111.00")));
        when(client.fetchDailyBars(eq("MSFT"), any(), eq(DAY)))
                .thenThrow(HttpClientErrorException.create(HttpStatus.BAD_GATEWAY, "bad", null, null, null));

        UsaCloseReconciler.Result result = reconciler("AAPL", "MSFT").reconcile(DAY);

        assertThat(result.saved()).isEqualTo(1);
        assertThat(result.failures()).containsExactly("MSFT");
        assertThat(savedRows()).hasSize(1);
    }

    @Test
    void 보정한_종목마다_일봉을_함께_저장한다() {
        when(repository.findAllByIdMarketAndIdSnapshotTime("USA", CLOSE))
                .thenReturn(List.of(row("AAPL", CLOSE, "110.00", "1100000", false)));
        when(client.fetchDailyBars(eq("AAPL"), any(), eq(DAY)))
                .thenReturn(List.of(bar(PREVIOUS_DAY, "100.00"), bar(DAY, "111.00")));

        reconciler("AAPL").reconcile(DAY);

        @SuppressWarnings("unchecked")
        ArgumentCaptor<Iterable<OverseasStockDaily>> captor = ArgumentCaptor.forClass(Iterable.class);
        verify(dailies).saveAll(captor.capture());
        assertThat(captor.getValue()).hasSize(1);
    }
}
