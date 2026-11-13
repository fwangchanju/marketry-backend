package kr.co.marketry.domain.stock.collector;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import kr.co.marketry.domain.stock.client.YahooFinanceQuoteClient;
import kr.co.marketry.domain.stock.client.YahooFinanceQuoteClient.DailyBar;
import kr.co.marketry.domain.stock.entity.MarketIndexDaily;
import kr.co.marketry.domain.stock.entity.OverseasStockQuote;
import kr.co.marketry.domain.stock.entity.OverseasStockQuoteId;
import kr.co.marketry.domain.stock.repository.MarketIndexDailyRepository;
import kr.co.marketry.domain.stock.repository.OverseasStockQuoteRepository;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class JapanCloseReconcilerTest {
    private final YahooFinanceQuoteClient client = mock(YahooFinanceQuoteClient.class);
    private final OverseasStockQuoteRepository repository = mock(OverseasStockQuoteRepository.class);
    private final MarketIndexDailyRepository indexes = mock(MarketIndexDailyRepository.class);
    private final JapanCloseReconciler reconciler =
            new JapanCloseReconciler(client, repository, indexes, Set.of("2914"));
    private final LocalDate date = LocalDate.of(2026, 11, 9);

    private DailyBar bar(LocalDate day, String close) {
        BigDecimal price = new BigDecimal(close);
        return new DailyBar(day, price, price, price, price, 1);
    }

    private void baseline() {
        var quote = OverseasStockQuote.create(
                new OverseasStockQuoteId("JAPAN", "2914", date.atTime(15, 30)),
                new BigDecimal("6269"),
                new BigDecimal("6269"),
                BigDecimal.ZERO,
                new BigDecimal("12538000"));
        when(repository.findFirstByIdMarketOrderByIdSnapshotTimeDesc("JAPAN")).thenReturn(Optional.of(quote));
        when(repository.findAllByIdMarketAndIdSnapshotTime("JAPAN", date.atTime(15, 30)))
                .thenReturn(List.of(quote));
        when(client.fetchDailyBars(eq("^N225"), any(), eq(date))).thenReturn(List.of(bar(date, "100")));
    }

    @Test
    void 기준_종목이_부족하면_정상_완료로_처리하지_않는다() {
        baseline();
        var incomplete = new JapanCloseReconciler(client, repository, indexes, Set.of("2914", "7203"));

        var result = incomplete.reconcile(date);

        assertThat(result.failures()).containsExactly("기준 종목 불일치: 누락=[7203] 추가=[]");
        verify(repository, never()).saveAll(any());
    }

    @Test
    void 개수가_같아도_종목코드가_다르면_보정하지_않는다() {
        baseline();
        var different = new JapanCloseReconciler(client, repository, indexes, Set.of("7203"));

        var result = different.reconcile(date);

        assertThat(result.failures()).containsExactly("기준 종목 불일치: 누락=[7203] 추가=[2914]");
        verify(repository, never()).saveAll(any());
    }

    @Test
    void 확인된_일본_휴장일과_거래일을_구분한다() {
        assertThat(JapanMarketReference.isKnownHoliday(LocalDate.of(2026, 11, 23)))
                .isTrue();
        assertThat(JapanMarketReference.isKnownHoliday(LocalDate.of(2027, 3, 22)))
                .isTrue();
        assertThat(JapanMarketReference.isKnownHoliday(date)).isFalse();
        assertThat(JapanMarketReference.constituentCodes()).hasSize(225).contains("2914", "7203");
    }

    @Test
    void 지수_일봉_조회가_한번_실패해도_다시_조회한다() {
        baseline();
        when(client.fetchDailyBars(eq("^N225"), any(), eq(date)))
                .thenThrow(new IllegalStateException())
                .thenReturn(List.of(bar(date, "100")));
        when(client.fetchDailyBars(eq("2914.T"), any(), eq(date)))
                .thenReturn(List.of(bar(date.minusDays(3), "6269"), bar(date, "5269")));

        var result = reconciler.reconcile(date);

        assertThat(result.saved()).isEqualTo(1);
        verify(client, times(2)).fetchDailyBars(eq("^N225"), any(), eq(date));
    }

    @Test
    void 같은_마감시각의_이전값을_일봉으로_갱신한다() {
        baseline();
        when(client.fetchDailyBars(eq("2914.T"), any(), eq(date)))
                .thenReturn(List.of(bar(date.minusDays(3), "6269"), bar(date, "5269")));

        var result = reconciler.reconcile(date);

        assertThat(result.saved()).isEqualTo(1);
        @SuppressWarnings("unchecked")
        ArgumentCaptor<Iterable<OverseasStockQuote>> captured = ArgumentCaptor.forClass(Iterable.class);
        verify(repository).saveAll(captured.capture());
        var saved = captured.getValue().iterator().next();
        assertThat(saved.getCurrentPrice()).isEqualByComparingTo("5269");
        assertThat(saved.getLastPrice()).isEqualByComparingTo("6269");
        assertThat(saved.getChangeRate()).isEqualByComparingTo("-15.95");
        assertThat(saved.getId().getSnapshotTime()).isEqualTo(date.atTime(15, 30));
        assertThat(saved.getCloseVerified()).isTrue();
    }

    @Test
    void 거래일이면_닛케이_지수_일봉을_저장한다() {
        baseline();
        when(client.fetchDailyBars(eq("^N225"), any(), eq(date)))
                .thenReturn(List.of(bar(date.minusDays(3), "99"), bar(date, "100")));
        when(client.fetchDailyBars(eq("2914.T"), any(), eq(date)))
                .thenReturn(List.of(bar(date.minusDays(3), "6269"), bar(date, "5269")));

        reconciler.reconcile(date);

        ArgumentCaptor<MarketIndexDaily> captured = ArgumentCaptor.forClass(MarketIndexDaily.class);
        verify(indexes).save(captured.capture());
        assertThat(captured.getValue().getId().getMarket()).isEqualTo("NIKKEI225");
        assertThat(captured.getValue().getId().getTradeDate()).isEqualTo(date);
        assertThat(captured.getValue().getCloseValue()).isEqualByComparingTo("100");
    }

    @Test
    void 지수_일봉이_없는_휴장일에는_지수를_저장하지_않는다() {
        baseline();
        when(client.fetchDailyBars(eq("^N225"), any(), eq(date))).thenReturn(List.of(bar(date.minusDays(3), "99")));

        reconciler.reconcile(date);

        verify(indexes, never()).save(any());
    }

    @Test
    void 지수_저장이_실패해도_종목_종가_보정은_계속한다() {
        baseline();
        when(indexes.save(any())).thenThrow(new IllegalStateException("저장 실패"));
        when(client.fetchDailyBars(eq("2914.T"), any(), eq(date)))
                .thenReturn(List.of(bar(date.minusDays(3), "6269"), bar(date, "5269")));

        var result = reconciler.reconcile(date);

        assertThat(result.saved()).isEqualTo(1);
        verify(repository).saveAll(any());
    }

    @Test
    void 종목_조회가_계속_실패하면_재시도하고_부분_저장하지_않는다() {
        baseline();
        when(client.fetchDailyBars(eq("2914.T"), any(), eq(date))).thenThrow(new IllegalStateException());

        var result = reconciler.reconcile(date);

        assertThat(result.failures()).containsExactly("2914");
        verify(client, times(3)).fetchDailyBars(eq("2914.T"), any(), eq(date));
        verify(repository, never()).saveAll(any());
    }

    @Test
    void 완전한_마감_스냅샷이_있으면_성공한_종목만_보정한다() {
        baseline();
        var latest =
                repository.findFirstByIdMarketOrderByIdSnapshotTimeDesc("JAPAN").orElseThrow();
        var other = OverseasStockQuote.create(
                new OverseasStockQuoteId("JAPAN", "7203", date.atTime(15, 30)),
                new BigDecimal("100"),
                new BigDecimal("90"),
                new BigDecimal("11.11"),
                new BigDecimal("1000"));
        when(repository.findAllByIdMarketAndIdSnapshotTime("JAPAN", date.atTime(15, 30)))
                .thenReturn(List.of(latest, other));
        var twoStocks = new JapanCloseReconciler(client, repository, indexes, Set.of("2914", "7203"));
        when(client.fetchDailyBars(eq("2914.T"), any(), eq(date)))
                .thenReturn(List.of(bar(date.minusDays(3), "6269"), bar(date, "5269")));
        when(client.fetchDailyBars(eq("7203.T"), any(), eq(date))).thenThrow(new IllegalStateException());

        var result = twoStocks.reconcile(date);

        assertThat(result.failures()).containsExactly("7203");
        assertThat(result.saved()).isEqualTo(1);
        verify(client, times(1)).fetchDailyBars(eq("2914.T"), any(), eq(date));
        verify(client, times(3)).fetchDailyBars(eq("7203.T"), any(), eq(date));
        verify(repository).saveAll(any());
    }

    @Test
    void 마감_스냅샷이_없고_일부만_성공하면_불완전한_새_스냅샷을_만들지_않는다() {
        var lastTime = date.atTime(15, 25);
        var a = OverseasStockQuote.create(
                new OverseasStockQuoteId("JAPAN", "2914", lastTime),
                new BigDecimal("6269"),
                new BigDecimal("6269"),
                BigDecimal.ZERO,
                new BigDecimal("12538000"));
        var b = OverseasStockQuote.create(
                new OverseasStockQuoteId("JAPAN", "7203", lastTime),
                new BigDecimal("100"),
                new BigDecimal("90"),
                new BigDecimal("11.11"),
                new BigDecimal("1000"));
        when(repository.findFirstByIdMarketOrderByIdSnapshotTimeDesc("JAPAN")).thenReturn(Optional.of(a));
        when(repository.findAllByIdMarketAndIdSnapshotTime("JAPAN", lastTime)).thenReturn(List.of(a, b));
        when(client.fetchDailyBars(eq("^N225"), any(), eq(date))).thenReturn(List.of(bar(date, "100")));
        when(client.fetchDailyBars(eq("2914.T"), any(), eq(date)))
                .thenReturn(List.of(bar(date.minusDays(3), "6269"), bar(date, "5269")));
        when(client.fetchDailyBars(eq("7203.T"), any(), eq(date))).thenThrow(new IllegalStateException());

        var result = new JapanCloseReconciler(client, repository, indexes, Set.of("2914", "7203")).reconcile(date);

        assertThat(result.failures()).containsExactly("7203");
        assertThat(result.saved()).isZero();
        verify(repository, never()).saveAll(any());
    }
}
