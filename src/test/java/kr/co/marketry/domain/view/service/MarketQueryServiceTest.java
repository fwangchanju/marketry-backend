package kr.co.marketry.domain.view.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import kr.co.marketry.common.enums.Market;
import kr.co.marketry.common.util.KstClock;
import kr.co.marketry.domain.stock.entity.MarketOverviewSnapshot;
import kr.co.marketry.domain.stock.repository.MarketOverviewSnapshotRepository;
import kr.co.marketry.domain.stock.service.SectorPriceSnapshotService;
import kr.co.marketry.domain.view.dto.MarketOverviewTrendItem;
import kr.co.marketry.domain.view.enums.MarketQuery;
import org.junit.jupiter.api.Test;

class MarketQueryServiceTest {

    private final MarketOverviewSnapshotRepository repository = mock(MarketOverviewSnapshotRepository.class);
    private final SectorPriceSnapshotService sectorPriceSnapshotService = mock(SectorPriceSnapshotService.class);
    private final TreemapQueryService treemapQueryService = mock(TreemapQueryService.class);
    private final MarketQueryService service =
            new MarketQueryService(repository, sectorPriceSnapshotService, treemapQueryService);

    @Test
    void 시장_추이는_마켓별_날짜별_마지막_수집값_최근_5일을_반환한다() {
        LocalDateTime now = KstClock.now();
        List<MarketOverviewSnapshot> snapshots = new ArrayList<>();
        for (int daysAgo = 5; daysAgo >= 0; daysAgo--) {
            LocalDate date = now.toLocalDate().minusDays(daysAgo);
            for (Market market : List.of(Market.KOSPI, Market.KOSDAQ)) {
                snapshots.add(snapshot(market, date.atTime(10, 0), "1.0"));
                snapshots.add(snapshot(market, date.atTime(15, 30), "2.0"));
            }
        }
        MarketOverviewSnapshot latest = snapshots.get(snapshots.size() - 1);
        when(repository.findBySnapshotTimeGreaterThanEqualAndSnapshotTimeLessThanOrderBySnapshotTimeAsc(
                        any(LocalDateTime.class), any(LocalDateTime.class)))
                .thenReturn(snapshots);
        when(repository.findFirstByOrderBySnapshotTimeDesc()).thenReturn(Optional.of(latest));
        when(repository.findBySnapshotTime(latest.getSnapshotTime())).thenReturn(List.of(latest));
        when(sectorPriceSnapshotService.findLatestCommonSnapshotTime(MarketQuery.ALL_STOCK.toMarkets()))
                .thenReturn(Optional.empty());
        when(sectorPriceSnapshotService.findClosingSnapshotTimes(
                        MarketQuery.ALL_STOCK.toMarkets(),
                        java.time.YearMonth.from(now).minusMonths(0)))
                .thenReturn(java.util.Map.of());
        when(sectorPriceSnapshotService.findClosingSnapshotTimes(
                        MarketQuery.ALL_STOCK.toMarkets(),
                        java.time.YearMonth.from(now).minusMonths(1)))
                .thenReturn(java.util.Map.of());
        when(sectorPriceSnapshotService.findClosingSnapshotTimes(
                        MarketQuery.ALL_STOCK.toMarkets(),
                        java.time.YearMonth.from(now).minusMonths(2)))
                .thenReturn(java.util.Map.of());

        var response = service.getMarketSummary();

        assertThat(response.marketOverviewTrends())
                .filteredOn(item -> item.market() == Market.KOSPI)
                .extracting(MarketOverviewTrendItem::changeRate)
                .containsExactly(
                        new BigDecimal("2.0"),
                        new BigDecimal("2.0"),
                        new BigDecimal("2.0"),
                        new BigDecimal("2.0"),
                        new BigDecimal("2.0"));
        assertThat(response.marketOverviewTrends()).hasSize(10);
    }

    private MarketOverviewSnapshot snapshot(Market market, LocalDateTime time, String changeRate) {
        return MarketOverviewSnapshot.create(
                market,
                time,
                BigDecimal.TEN,
                BigDecimal.ONE,
                new BigDecimal(changeRate),
                BigDecimal.TEN,
                "정규장",
                1,
                1,
                0,
                0,
                0,
                time);
    }
}
