package kr.co.marketry.domain.view.service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import kr.co.marketry.common.enums.Market;
import kr.co.marketry.common.util.KstClock;
import kr.co.marketry.domain.stock.entity.MarketOverviewSnapshot;
import kr.co.marketry.domain.stock.repository.MarketOverviewSnapshotRepository;
import kr.co.marketry.domain.stock.service.SectorPriceSnapshotService;
import kr.co.marketry.domain.view.dto.MarketOverviewItem;
import kr.co.marketry.domain.view.dto.MarketOverviewTrendItem;
import kr.co.marketry.domain.view.dto.MarketSectorTrendItem;
import kr.co.marketry.domain.view.dto.MarketSummaryResponse;
import kr.co.marketry.domain.view.dto.SnapshotResponse;
import kr.co.marketry.domain.view.dto.TreemapItem;
import kr.co.marketry.domain.view.dto.TreemapResponse;
import kr.co.marketry.domain.view.dto.TreemapSectorNode;
import kr.co.marketry.domain.view.enums.MarketQuery;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
@RequiredArgsConstructor
public class MarketQueryService {

    private final MarketOverviewSnapshotRepository marketOverviewSnapshotRepository;
    private final SectorPriceSnapshotService sectorPriceSnapshotService;
    private final TreemapQueryService treemapQueryService;

    /** 홈이 쓰는 시장 종합 현황(지수·등락·상하한가) 반환 */
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    public MarketSummaryResponse getMarketSummary() {
        return new MarketSummaryResponse(getMarketOverviews(), getMarketOverviewTrends(), getSectorOverviewTrends());
    }

    private List<MarketSectorTrendItem> getSectorOverviewTrends() {
        LocalDate today = KstClock.now().toLocalDate();
        List<Market> markets = List.of(Market.KOSPI, Market.KOSDAQ);
        TreeMap<LocalDate, LocalDateTime> snapshotTimes = new TreeMap<>();
        sectorPriceSnapshotService
                .findLatestCommonSnapshotTime(markets)
                .ifPresent(snapshotTime -> snapshotTimes.put(snapshotTime.toLocalDate(), snapshotTime));

        for (int monthOffset = 0; monthOffset < 3 && snapshotTimes.size() < 5; monthOffset++) {
            YearMonth month = YearMonth.from(today).minusMonths(monthOffset);
            sectorPriceSnapshotService.findClosingSnapshotTimes(markets, month).forEach(snapshotTimes::putIfAbsent);
        }

        List<MarketSectorTrendItem> trends = new ArrayList<>();
        snapshotTimes.descendingMap().entrySet().stream()
                .limit(5)
                .sorted(Map.Entry.comparingByKey())
                .forEach(entry -> {
                    TreemapResponse treemap =
                            treemapQueryService.getDefaultTreemap(MarketQuery.ALL_STOCK, entry.getValue());
                    for (TreemapSectorNode sector : treemap.items()) {
                        BigDecimal weightedSum = BigDecimal.ZERO;
                        BigDecimal totalMarketValue = BigDecimal.ZERO;
                        for (TreemapItem stock : sector.items()) {
                            weightedSum = weightedSum.add(stock.changeRate().multiply(stock.totalMarketValue()));
                            totalMarketValue = totalMarketValue.add(stock.totalMarketValue());
                        }
                        if (totalMarketValue.signum() > 0) {
                            trends.add(new MarketSectorTrendItem(
                                    entry.getKey(),
                                    sector.sectorName(),
                                    weightedSum.divide(totalMarketValue, 6, RoundingMode.HALF_UP)));
                        }
                    }
                });
        return trends;
    }

    private java.util.List<MarketOverviewTrendItem> getMarketOverviewTrends() {
        LocalDateTime now = KstClock.now();
        // 최근 5거래일을 공휴일·연휴 뒤에도 채울 수 있도록 45일 범위에서 날짜별 마지막 수집값을 고른다.
        LocalDateTime from = now.toLocalDate().minusDays(45).atStartOfDay();
        Map<Market, Map<LocalDate, MarketOverviewSnapshot>> latestByMarketAndDate = new EnumMap<>(Market.class);
        for (Market market : Market.values()) {
            latestByMarketAndDate.put(market, new java.util.TreeMap<>());
        }

        for (MarketOverviewSnapshot snapshot :
                marketOverviewSnapshotRepository
                        .findBySnapshotTimeGreaterThanEqualAndSnapshotTimeLessThanOrderBySnapshotTimeAsc(from, now)) {
            latestByMarketAndDate
                    .computeIfAbsent(snapshot.getMarketType(), ignored -> new java.util.TreeMap<>())
                    .put(snapshot.getSnapshotTime().toLocalDate(), snapshot);
        }

        return latestByMarketAndDate.values().stream()
                .flatMap(byDate -> byDate.entrySet().stream()
                        .sorted(Map.Entry.<LocalDate, MarketOverviewSnapshot>comparingByKey()
                                .reversed())
                        .limit(5)
                        .sorted(Map.Entry.comparingByKey())
                        .map(entry -> MarketOverviewTrendItem.from(entry.getValue())))
                .toList();
    }

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
}
