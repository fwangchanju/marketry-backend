package dev.eolmae.marketry.domain.view.service;

import dev.eolmae.marketry.common.enums.Market;
import dev.eolmae.marketry.domain.stock.entity.IndexContributionRankingSnapshot;
import dev.eolmae.marketry.domain.stock.entity.InvestorTradingSummarySnapshot;
import dev.eolmae.marketry.domain.stock.entity.MarketOverviewSnapshot;
import dev.eolmae.marketry.domain.stock.entity.ProgramTradingRankingSnapshot;
import dev.eolmae.marketry.domain.stock.enums.AmtQty;
import dev.eolmae.marketry.domain.stock.enums.ProgramRanking;
import dev.eolmae.marketry.domain.stock.repository.IndexContributionRankingSnapshotRepository;
import dev.eolmae.marketry.domain.stock.repository.InvestorTradingSummarySnapshotRepository;
import dev.eolmae.marketry.domain.stock.repository.MarketOverviewSnapshotRepository;
import dev.eolmae.marketry.domain.stock.repository.ProgramTradingRankingSnapshotRepository;
import dev.eolmae.marketry.domain.view.dto.IndexContributionItem;
import dev.eolmae.marketry.domain.view.dto.InvestorTradingSummaryItem;
import dev.eolmae.marketry.domain.view.dto.MarketOverviewItem;
import dev.eolmae.marketry.domain.view.dto.MarketSummaryResponse;
import dev.eolmae.marketry.domain.view.dto.ProgramTradingRankingItem;
import dev.eolmae.marketry.domain.view.dto.SnapshotResponse;
import dev.eolmae.marketry.domain.view.enums.MarketQuery;
import dev.eolmae.marketry.domain.view.enums.RankingType;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
@RequiredArgsConstructor
public class MarketQueryService {

    private final MarketOverviewSnapshotRepository marketOverviewSnapshotRepository;
    private final InvestorTradingSummarySnapshotRepository investorTradingSummarySnapshotRepository;

    private final ProgramTradingRankingSnapshotRepository programTradingRankingSnapshotRepository;
    private final IndexContributionRankingSnapshotRepository indexContributionRankingSnapshotRepository;

    private static final int RANKING_LIMIT = 10;

    /** 메인 대시보드용 시장 종합 현황 반환 — HTS에서 화면 여러 개를 오가며 보던 표 7개를 한 화면에 모은 것 */
    public MarketSummaryResponse getMarketSummary() {
        record SummaryDefaults(Market market, AmtQty amtQty, RankingType ranking) {}

        var summaryDefaults = new SummaryDefaults(Market.KOSPI, AmtQty.AMOUNT, RankingType.NET_BUY);

        return new MarketSummaryResponse(
                getMarketOverviews(),
                getInvestorTradingSummaries(),
                getProgramTradingRankings(
                        summaryDefaults.market(), summaryDefaults.ranking(), summaryDefaults.amtQty()),
                getIndexContribution(summaryDefaults.market()));
    }

    /** 대시보드 요약: 시장 종합 현황 (지수/등락/상하한가) */
    private SnapshotResponse<MarketOverviewItem> getMarketOverviews() {
        LocalDateTime latestSnapshotTime = marketOverviewSnapshotRepository
                .findFirstByOrderBySnapshotTimeDesc()
                .map(MarketOverviewSnapshot::getSnapshotTime)
                .orElse(null);
        if (latestSnapshotTime == null) {
            return SnapshotResponse.empty();
        }

        // marketType은 @Enumerated(STRING)이라 DB ORDER BY는 문자열 순(KOSDAQ < KOSPI)이 됨 -
        // enum 선언 순서(KOSPI, KOSDAQ)대로 나오도록 Java에서 재정렬.
        var snapshots = marketOverviewSnapshotRepository.findBySnapshotTime(latestSnapshotTime).stream()
                .sorted(Comparator.comparing(MarketOverviewSnapshot::getMarketType))
                .toList();

        return new SnapshotResponse<>(
                latestSnapshotTime,
                snapshots.stream().map(MarketOverviewItem::from).toList());
    }

    /** 대시보드 요약: 투자자별 매매 종합 */
    private SnapshotResponse<InvestorTradingSummaryItem> getInvestorTradingSummaries() {
        LocalDateTime latestSnapshotTime = investorTradingSummarySnapshotRepository
                .findFirstByOrderBySnapshotTimeDesc()
                .map(InvestorTradingSummarySnapshot::getSnapshotTime)
                .orElse(null);
        if (latestSnapshotTime == null) {
            return SnapshotResponse.empty();
        }

        // marketType/investor 둘 다 @Enumerated(STRING)이라 DB ORDER BY는 문자열 순이 됨 -
        // enum 선언 순서(시장: KOSPI, KOSDAQ / 투자자: PERSONAL, FOREIGNER, ...)대로 Java에서 재정렬.
        var snapshots = investorTradingSummarySnapshotRepository.findBySnapshotTime(latestSnapshotTime).stream()
                .sorted(Comparator.comparing(InvestorTradingSummarySnapshot::getMarketType)
                        .thenComparing(InvestorTradingSummarySnapshot::getInvestor))
                .toList();

        return new SnapshotResponse<>(
                latestSnapshotTime,
                snapshots.stream()
                        .map(item -> new InvestorTradingSummaryItem(
                                item.getMarketType(),
                                item.getInvestor(),
                                item.getBuyAmount(),
                                item.getSellAmount(),
                                item.getNetBuyAmount(),
                                item.getSnapshotTime()))
                        .toList());
    }

    public SnapshotResponse<ProgramTradingRankingItem> getProgramTradingRankings(
            MarketQuery market, RankingType ranking, AmtQty amtQty) {
        return getProgramTradingRankings(market.toMarkets(), ProgramRanking.from(ranking), amtQty);
    }

    private SnapshotResponse<ProgramTradingRankingItem> getProgramTradingRankings(
            Market market, RankingType ranking, AmtQty amtQty) {
        return getProgramTradingRankings(List.of(market), ProgramRanking.from(ranking), amtQty);
    }

    /** 프로그램매매 순매수/순매도 상위 top 10 반환 */
    private SnapshotResponse<ProgramTradingRankingItem> getProgramTradingRankings(
            List<Market> markets, ProgramRanking ranking, AmtQty amtQty) {
        LocalDateTime latestSnapshotTime = programTradingRankingSnapshotRepository
                .findFirstByMarketTypeInAndRankingTypeAndAmtQtyOrderBySnapshotTimeDesc(markets, ranking, amtQty)
                .map(ProgramTradingRankingSnapshot::getSnapshotTime)
                .orElse(null);
        if (latestSnapshotTime == null) {
            return SnapshotResponse.empty();
        }

        var snapshots =
                programTradingRankingSnapshotRepository.findByMarketTypeInAndRankingTypeAndAmtQtyAndSnapshotTime(
                        markets, ranking, amtQty, latestSnapshotTime);

        // 종목당 거래소(exchangeType)별로 행이 나뉘어 있어 종목 기준으로 금액을 합산한 뒤,
        // market=ALL_STOCK(KOSPI+KOSDAQ)까지 포함해 netBuyAmount 기준으로 다시 정렬
        Map<String, AggregatedProgramRanking> merged = snapshots.stream()
                .collect(Collectors.toMap(
                        ProgramTradingRankingSnapshot::getStockCode,
                        MarketQueryService::toAggregated,
                        MarketQueryService::mergeAggregated));

        var sorted = merged.values().stream()
                .sorted(Comparator.comparing(AggregatedProgramRanking::netBuyAmount, ranking.valueComparator()))
                .limit(RANKING_LIMIT)
                .toList();

        List<ProgramTradingRankingItem> items = new ArrayList<>();
        int rank = 1;
        for (AggregatedProgramRanking agg : sorted) {
            items.add(new ProgramTradingRankingItem(
                    rank++,
                    agg.stockCode(),
                    agg.stockName(),
                    agg.buyAmount(),
                    agg.sellAmount(),
                    ranking.normalize(agg.netBuyAmount()),
                    agg.snapshotTime()));
        }

        return new SnapshotResponse<>(latestSnapshotTime, items);
    }

    private static AggregatedProgramRanking toAggregated(ProgramTradingRankingSnapshot snapshot) {
        return new AggregatedProgramRanking(
                snapshot.getStockCode(),
                snapshot.getStockName(),
                snapshot.getProgramBuyAmount(),
                snapshot.getProgramSellAmount(),
                snapshot.getProgramNetBuyAmount(),
                snapshot.getSnapshotTime());
    }

    private static AggregatedProgramRanking mergeAggregated(AggregatedProgramRanking a, AggregatedProgramRanking b) {
        return new AggregatedProgramRanking(
                a.stockCode(),
                a.stockName(),
                a.buyAmount().add(b.buyAmount()),
                a.sellAmount().add(b.sellAmount()),
                a.netBuyAmount().add(b.netBuyAmount()),
                a.snapshotTime());
    }

    private record AggregatedProgramRanking(
            String stockCode,
            String stockName,
            BigDecimal buyAmount,
            BigDecimal sellAmount,
            BigDecimal netBuyAmount,
            LocalDateTime snapshotTime) {}

    /** 지수 기여도 상위 종목 반환 */
    public SnapshotResponse<IndexContributionItem> getIndexContribution(Market market) {
        LocalDateTime latestSnapshotTime = indexContributionRankingSnapshotRepository
                .findFirstByMarketTypeOrderBySnapshotTimeDesc(market)
                .map(IndexContributionRankingSnapshot::getSnapshotTime)
                .orElse(null);
        if (latestSnapshotTime == null) {
            return SnapshotResponse.empty();
        }

        var snapshots = indexContributionRankingSnapshotRepository.findByMarketTypeAndSnapshotTimeOrderByRankAsc(
                market, latestSnapshotTime);

        var items = snapshots.stream()
                .map(item -> new IndexContributionItem(
                        item.getMarketType(),
                        item.getRank(),
                        item.getStockCode(),
                        item.getStockName(),
                        item.getContributionScore(),
                        item.getPriceChangeRate(),
                        item.getSnapshotTime()))
                .toList();

        return new SnapshotResponse<>(latestSnapshotTime, items);
    }
}
