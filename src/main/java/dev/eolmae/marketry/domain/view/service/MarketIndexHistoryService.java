package dev.eolmae.marketry.domain.view.service;

import dev.eolmae.marketry.common.enums.Market;
import dev.eolmae.marketry.domain.stock.entity.MarketOverviewSnapshot;
import dev.eolmae.marketry.domain.stock.repository.MarketOverviewSnapshotRepository;
import dev.eolmae.marketry.domain.stock.service.MarketCalendarTimeService;
import dev.eolmae.marketry.domain.view.dto.MarketOverviewItem;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
@RequiredArgsConstructor
public class MarketIndexHistoryService {

    private final MarketOverviewSnapshotRepository repository;
    private final MarketCalendarTimeService calendarTimeService;

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
        return closingByDate.values().stream()
                .flatMap(byMarket -> byMarket.values().stream())
                .map(MarketOverviewItem::from)
                .toList();
    }
}
