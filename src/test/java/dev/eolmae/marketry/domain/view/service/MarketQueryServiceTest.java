package dev.eolmae.marketry.domain.view.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import dev.eolmae.marketry.common.enums.Market;
import dev.eolmae.marketry.domain.stock.entity.ProgramTradingRankingSnapshot;
import dev.eolmae.marketry.domain.stock.enums.AmtQty;
import dev.eolmae.marketry.domain.stock.enums.ExchangeType;
import dev.eolmae.marketry.domain.stock.enums.ProgramRanking;
import dev.eolmae.marketry.domain.stock.repository.IndexContributionRankingSnapshotRepository;
import dev.eolmae.marketry.domain.stock.repository.InvestorTradingSummarySnapshotRepository;
import dev.eolmae.marketry.domain.stock.repository.MarketOverviewSnapshotRepository;
import dev.eolmae.marketry.domain.stock.repository.ProgramTradingRankingSnapshotRepository;
import dev.eolmae.marketry.domain.view.dto.ProgramTradingRankingItem;
import dev.eolmae.marketry.domain.view.enums.MarketQuery;
import dev.eolmae.marketry.domain.view.enums.RankingType;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

class MarketQueryServiceTest {

    private final ProgramTradingRankingSnapshotRepository programTradingRankingSnapshotRepository =
            Mockito.mock(ProgramTradingRankingSnapshotRepository.class);
    private final MarketQueryService service = new MarketQueryService(
            Mockito.mock(MarketOverviewSnapshotRepository.class),
            Mockito.mock(InvestorTradingSummarySnapshotRepository.class),
            programTradingRankingSnapshotRepository,
            Mockito.mock(IndexContributionRankingSnapshotRepository.class));

    private final LocalDateTime snapshotTime = LocalDateTime.of(2025, 6, 2, 10, 0);

    @Test
    void getProgramTradingRankings_같은_종목의_거래소별_행이_합산된다() {
        stub(
                ProgramRanking.NET_BUY,
                snapshot("005930", ExchangeType.KRX, 1000, 400, 600),
                snapshot("005930", ExchangeType.NXT, 500, 200, 300));

        List<ProgramTradingRankingItem> items = service.getProgramTradingRankings(
                        MarketQuery.KOSPI, RankingType.NET_BUY, AmtQty.AMOUNT)
                .items();

        assertThat(items).hasSize(1);
        ProgramTradingRankingItem merged = items.getFirst();
        assertThat(merged.stockCode()).isEqualTo("005930");
        assertThat(merged.programBuyAmount()).isEqualByComparingTo("1500");
        assertThat(merged.programSellAmount()).isEqualByComparingTo("600");
        assertThat(merged.programNetBuyAmount()).isEqualByComparingTo("900");
    }

    @Test
    void getProgramTradingRankings_순매수는_큰_금액부터_순위가_매겨진다() {
        stub(
                ProgramRanking.NET_BUY,
                snapshot("000660", ExchangeType.KRX, 100, 0, 100),
                snapshot("005930", ExchangeType.KRX, 300, 0, 300),
                snapshot("035420", ExchangeType.KRX, 200, 0, 200));

        List<ProgramTradingRankingItem> items = service.getProgramTradingRankings(
                        MarketQuery.KOSPI, RankingType.NET_BUY, AmtQty.AMOUNT)
                .items();

        assertThat(items)
                .extracting(ProgramTradingRankingItem::rank, ProgramTradingRankingItem::stockCode)
                .containsExactly(
                        org.assertj.core.groups.Tuple.tuple(1, "005930"),
                        org.assertj.core.groups.Tuple.tuple(2, "035420"),
                        org.assertj.core.groups.Tuple.tuple(3, "000660"));
    }

    @Test
    void getProgramTradingRankings_순매도는_절댓값이_큰_순으로_정렬되고_양수로_표시된다() {
        stub(
                ProgramRanking.NET_SELL,
                snapshot("000660", ExchangeType.KRX, 0, 100, -100),
                snapshot("005930", ExchangeType.KRX, 0, 900, -900));

        List<ProgramTradingRankingItem> items = service.getProgramTradingRankings(
                        MarketQuery.KOSPI, RankingType.NET_SELL, AmtQty.AMOUNT)
                .items();

        assertThat(items)
                .extracting(ProgramTradingRankingItem::stockCode, ProgramTradingRankingItem::programNetBuyAmount)
                .containsExactly(
                        org.assertj.core.groups.Tuple.tuple("005930", BigDecimal.valueOf(900)),
                        org.assertj.core.groups.Tuple.tuple("000660", BigDecimal.valueOf(100)));
    }

    @Test
    void getProgramTradingRankings_10위까지만_반환된다() {
        ProgramTradingRankingSnapshot[] snapshots = new ProgramTradingRankingSnapshot[11];
        for (int i = 0; i < 11; i++) {
            snapshots[i] = snapshot("00000" + i, ExchangeType.KRX, i + 1, 0, i + 1);
        }
        stub(ProgramRanking.NET_BUY, snapshots);

        List<ProgramTradingRankingItem> items = service.getProgramTradingRankings(
                        MarketQuery.KOSPI, RankingType.NET_BUY, AmtQty.AMOUNT)
                .items();

        assertThat(items).hasSize(10);
    }

    private void stub(ProgramRanking ranking, ProgramTradingRankingSnapshot... snapshots) {
        when(programTradingRankingSnapshotRepository
                        .findFirstByMarketTypeInAndRankingTypeAndAmtQtyOrderBySnapshotTimeDesc(
                                any(), Mockito.eq(ranking), any()))
                .thenReturn(Optional.of(snapshots[0]));
        when(programTradingRankingSnapshotRepository.findByMarketTypeInAndRankingTypeAndAmtQtyAndSnapshotTime(
                        any(), Mockito.eq(ranking), any(), any()))
                .thenReturn(List.of(snapshots));
    }

    private ProgramTradingRankingSnapshot snapshot(
            String stockCode, ExchangeType exchangeType, long buy, long sell, long net) {
        return ProgramTradingRankingSnapshot.create(
                Market.KOSPI,
                AmtQty.AMOUNT,
                ProgramRanking.NET_BUY,
                snapshotTime,
                stockCode,
                exchangeType,
                stockCode + "-종목",
                BigDecimal.valueOf(buy),
                BigDecimal.valueOf(sell),
                BigDecimal.valueOf(net));
    }
}
