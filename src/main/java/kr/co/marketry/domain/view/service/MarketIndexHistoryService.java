package kr.co.marketry.domain.view.service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.TreeMap;
import kr.co.marketry.common.enums.Market;
import kr.co.marketry.domain.stock.entity.MarketIndexDaily;
import kr.co.marketry.domain.stock.entity.MarketOverviewSnapshot;
import kr.co.marketry.domain.stock.repository.MarketIndexDailyRepository;
import kr.co.marketry.domain.stock.repository.MarketOverviewSnapshotRepository;
import kr.co.marketry.domain.stock.service.MarketCalendarTimeService;
import kr.co.marketry.domain.view.dto.MarketOverviewItem;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
@RequiredArgsConstructor
public class MarketIndexHistoryService {
    private static final int DAILY_LOOKBACK_DAYS = 14;
    private static final LocalTime DAILY_SNAPSHOT_TIME = LocalTime.of(15, 40);

    private final MarketOverviewSnapshotRepository repository;
    private final MarketCalendarTimeService calendarTimeService;
    private final MarketIndexDailyRepository indexDailyRepository;

    /** 종목 종가 지도의 시각과 무관하게 날짜별 메인 마켓 종가 확정 구간의 마지막 지수를 고른다. */
    // 달력 조회가 REQUIRES_NEW이므로 이 메서드에서 연결을 점유한 채 두 번째 연결을 기다리지 않는다.
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    public List<MarketOverviewItem> getClosingHistory(YearMonth month) {
        var snapshots = repository.findBySnapshotTimeGreaterThanEqualAndSnapshotTimeLessThanOrderBySnapshotTimeAsc(
                month.atDay(1).atStartOfDay(), month.plusMonths(1).atDay(1).atStartOfDay());
        var times = calendarTimeService.resolveAll(snapshots.stream()
                .map(snapshot -> snapshot.getSnapshotTime().toLocalDate())
                .distinct()
                .toList());
        Map<LocalDate, Map<Market, MarketOverviewSnapshot>> closingByDate = new TreeMap<>();
        for (MarketOverviewSnapshot snapshot : snapshots) {
            LocalDate date = snapshot.getSnapshotTime().toLocalDate();
            var day = times.get(date);
            if (day.holiday()
                    || snapshot.getSnapshotTime().isBefore(day.regularMarketEnd())
                    || snapshot.getSnapshotTime().isBefore(day.closingWindowEnd()) == false) {
                continue;
            }
            closingByDate
                    .computeIfAbsent(date, ignored -> new EnumMap<>(Market.class))
                    .merge(
                            snapshot.getMarketType(),
                            snapshot,
                            (previous, current) ->
                                    current.getSnapshotTime().isAfter(previous.getSnapshotTime()) ? current : previous);
        }
        List<MarketOverviewItem> items = new ArrayList<>(closingByDate.values().stream()
                .flatMap(byMarket -> byMarket.values().stream())
                .map(MarketOverviewItem::from)
                .toList());
        items.addAll(dailyItemsWithoutSnapshot(month, closingByDate));
        // 기존 응답과 같이 날짜순, 같은 날은 시장 순서(코스피 → 코스닥)로 둔다.
        items.sort(Comparator.comparing(
                        (MarketOverviewItem item) -> item.snapshotTime().toLocalDate())
                .thenComparing(MarketOverviewItem::market));
        return items;
    }

    /** 시장 요약이 없는 날짜는 지수 일봉으로 채운다. 전날 값이 있어야 등락을 알 수 있어 달 시작 앞까지 함께 읽는다. */
    private List<MarketOverviewItem> dailyItemsWithoutSnapshot(
            YearMonth month, Map<LocalDate, Map<Market, MarketOverviewSnapshot>> closingByDate) {
        List<MarketOverviewItem> items = new ArrayList<>();
        Map<String, MarketIndexDaily> previousByMarket = new HashMap<>();
        for (MarketIndexDaily daily : indexDailyRepository.findByIdTradeDateBetweenOrderByIdTradeDateAsc(
                month.atDay(1).minusDays(DAILY_LOOKBACK_DAYS), month.atEndOfMonth())) {
            String marketName = daily.getId().getMarket();
            MarketIndexDaily previous = previousByMarket.put(marketName, daily);
            LocalDate date = daily.getId().getTradeDate();
            Optional<Market> market = Arrays.stream(Market.values())
                    .filter(candidate -> candidate.name().equals(marketName))
                    .findFirst();
            if (market.isEmpty()
                    || previous == null
                    || YearMonth.from(date).equals(month) == false
                    || closingByDate.getOrDefault(date, Map.of()).containsKey(market.get())) {
                continue;
            }
            BigDecimal change = daily.getCloseValue().subtract(previous.getCloseValue());
            items.add(MarketOverviewItem.fromDaily(
                    market.get(),
                    daily.getCloseValue(),
                    change,
                    change.multiply(BigDecimal.valueOf(100)).divide(previous.getCloseValue(), 4, RoundingMode.HALF_UP),
                    daily.getTradingValue() == null ? BigDecimal.ZERO : BigDecimal.valueOf(daily.getTradingValue()),
                    date.atTime(DAILY_SNAPSHOT_TIME)));
        }
        return items;
    }
}
