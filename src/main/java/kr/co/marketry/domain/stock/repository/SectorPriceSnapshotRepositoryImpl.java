package kr.co.marketry.domain.stock.repository;

import static kr.co.marketry.domain.stock.entity.QMarketCalendar.marketCalendar;
import static kr.co.marketry.domain.stock.entity.QSectorPriceSnapshot.sectorPriceSnapshot;

import com.querydsl.core.BooleanBuilder;
import com.querydsl.core.types.dsl.BooleanExpression;
import com.querydsl.core.types.dsl.Expressions;
import com.querydsl.jpa.JPAExpressions;
import com.querydsl.jpa.JPQLOps;
import com.querydsl.jpa.impl.JPAQueryFactory;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;
import kr.co.marketry.common.enums.Country;
import kr.co.marketry.common.enums.Market;
import kr.co.marketry.domain.stock.entity.SectorPriceSnapshot;
import kr.co.marketry.domain.stock.enums.MarketCalendarStatus;
import lombok.RequiredArgsConstructor;

@RequiredArgsConstructor
public class SectorPriceSnapshotRepositoryImpl implements SectorPriceSnapshotRepositoryCustom {

    private final JPAQueryFactory queryFactory;

    @Override
    public Optional<LocalDateTime> findLatestCommonSnapshotTime(List<Market> markets, LocalDate today) {
        LocalDate throughDate = today;
        while (true) {
            LocalDate date = findLatestTradingSnapshotDate(markets, throughDate);
            if (date == null) {
                return Optional.empty();
            }
            LocalDateTime latest = queryFactory
                    .select(sectorPriceSnapshot.snapshotTime)
                    .from(sectorPriceSnapshot)
                    .where(
                            sectorPriceSnapshot.marketType.in(markets),
                            sectorPriceSnapshot.snapshotTime.goe(date.atStartOfDay()),
                            sectorPriceSnapshot.snapshotTime.lt(date.plusDays(1).atStartOfDay()))
                    .groupBy(sectorPriceSnapshot.snapshotTime)
                    .having(sectorPriceSnapshot.marketType.countDistinct().eq((long) markets.size()))
                    .orderBy(sectorPriceSnapshot.snapshotTime.desc())
                    .limit(1)
                    .fetchOne();
            if (latest != null) {
                return Optional.of(latest);
            }
            // 두 시장에 가격은 있어도 공통 시각이 없으면 그 날짜의 통합 지도를 만들 수 없다.
            throughDate = date.minusDays(1);
        }
    }

    private LocalDate findLatestTradingSnapshotDate(List<Market> markets, LocalDate throughDate) {
        BooleanBuilder pricesPresent = new BooleanBuilder();
        for (Market market : markets) {
            pricesPresent.and(JPAExpressions.selectOne()
                    .from(sectorPriceSnapshot)
                    .where(sectorPriceSnapshot.marketType.eq(market), matchesCalendarDate())
                    .exists());
        }
        return queryFactory
                .select(marketCalendar.date)
                .from(marketCalendar)
                .where(
                        marketCalendar.country.eq(Country.KR),
                        marketCalendar.status.in(MarketCalendarStatus.TRADING_DAY, MarketCalendarStatus.FAILED),
                        marketCalendar.date.loe(throughDate),
                        pricesPresent)
                .orderBy(marketCalendar.date.desc())
                .limit(1)
                .fetchOne();
    }

    @Override
    public List<LocalDate> findTradingSnapshotDates(LocalDate today) {
        var latest = sectorPriceSnapshot.snapshotTime.max();
        return queryFactory
                .select(latest)
                .from(sectorPriceSnapshot)
                .where(
                        sectorPriceSnapshot.snapshotTime.lt(today.plusDays(1).atStartOfDay()),
                        JPAExpressions.selectOne()
                                .from(marketCalendar)
                                .where(
                                        marketCalendar.country.eq(Country.KR),
                                        marketCalendar.status.in(
                                                MarketCalendarStatus.TRADING_DAY, MarketCalendarStatus.FAILED),
                                        matchesCalendarDate())
                                .exists())
                .groupBy(
                        sectorPriceSnapshot.snapshotTime.year(),
                        sectorPriceSnapshot.snapshotTime.month(),
                        sectorPriceSnapshot.snapshotTime.dayOfMonth())
                .orderBy(latest.desc())
                .fetch()
                .stream()
                .map(LocalDateTime::toLocalDate)
                .toList();
    }

    private BooleanExpression matchesCalendarDate() {
        var start = Expressions.dateTimeOperation(
                LocalDateTime.class, JPQLOps.CAST, marketCalendar.date, Expressions.constant("LocalDateTime"));
        // QueryDSL의 ADD는 숫자 전용이므로 Hibernate의 날짜 산술 표현만 타입 지정한다.
        var end = Expressions.dateTimeTemplate(LocalDateTime.class, "({0} + 1 day)", start);
        return sectorPriceSnapshot.snapshotTime.goe(start).and(sectorPriceSnapshot.snapshotTime.lt(end));
    }

    @Override
    public List<LocalDate> findMultipleSnapshotDates(LocalDate throughDate) {
        var latest = sectorPriceSnapshot.snapshotTime.max();
        return queryFactory
                .select(latest)
                .from(sectorPriceSnapshot)
                .where(
                        sectorPriceSnapshot.snapshotTime.lt(
                                throughDate.plusDays(1).atStartOfDay()),
                        JPAExpressions.selectOne()
                                .from(marketCalendar)
                                .where(
                                        marketCalendar.country.eq(Country.KR),
                                        marketCalendar.status.eq(MarketCalendarStatus.TRADING_DAY),
                                        matchesCalendarDate())
                                .exists())
                .groupBy(
                        sectorPriceSnapshot.snapshotTime.year(),
                        sectorPriceSnapshot.snapshotTime.month(),
                        sectorPriceSnapshot.snapshotTime.dayOfMonth(),
                        sectorPriceSnapshot.marketType)
                .having(sectorPriceSnapshot.snapshotTime.countDistinct().gt(1L))
                .orderBy(latest.asc())
                .fetch()
                .stream()
                .map(LocalDateTime::toLocalDate)
                .distinct()
                .toList();
    }

    @Override
    public List<SnapshotDaySummary> findSnapshotDaySummaries(List<TimeWindow> windows) {
        if (windows.isEmpty()) {
            return List.of();
        }
        Map<LocalDate, TimeWindow> windowsByDate = windows.stream()
                .collect(Collectors.toMap(window -> window.from().toLocalDate(), Function.identity()));
        LocalDate start =
                windowsByDate.keySet().stream().min(LocalDate::compareTo).orElseThrow();
        LocalDate end =
                windowsByDate.keySet().stream().max(LocalDate::compareTo).orElseThrow();
        // 종목 행마다 날짜·시간표 경계를 반복 계산하지 않고 시장별 서로 다른 시각만 한 번에 가져온다.
        Map<LocalDate, Map<Market, List<LocalDateTime>>> timesByDate = queryFactory
                .select(sectorPriceSnapshot.marketType, sectorPriceSnapshot.snapshotTime)
                .distinct()
                .from(sectorPriceSnapshot)
                .where(
                        sectorPriceSnapshot.snapshotTime.goe(start.atStartOfDay()),
                        sectorPriceSnapshot.snapshotTime.lt(end.plusDays(1).atStartOfDay()))
                .fetch()
                .stream()
                .filter(tuple -> windowsByDate.containsKey(
                        tuple.get(sectorPriceSnapshot.snapshotTime).toLocalDate()))
                .collect(Collectors.groupingBy(
                        tuple -> tuple.get(sectorPriceSnapshot.snapshotTime).toLocalDate(),
                        Collectors.groupingBy(
                                tuple -> tuple.get(sectorPriceSnapshot.marketType),
                                Collectors.mapping(
                                        tuple -> tuple.get(sectorPriceSnapshot.snapshotTime), Collectors.toList()))));
        return timesByDate.entrySet().stream()
                .flatMap(day -> day.getValue().entrySet().stream()
                        .map(market -> new SnapshotDaySummary(
                                market.getKey(),
                                market.getValue().stream()
                                        .max(LocalDateTime::compareTo)
                                        .orElseThrow(),
                                market.getValue().size(),
                                market.getValue().stream()
                                        .filter(time -> time.isBefore(
                                                windowsByDate.get(day.getKey()).toExclusive()))
                                        .max(LocalDateTime::compareTo)
                                        .orElse(null))))
                .toList();
    }

    @Override
    public List<SectorPriceSnapshot> findByMarketSnapshotTimes(List<MarketSnapshotTime> times) {
        if (times.isEmpty()) {
            return List.of();
        }
        BooleanBuilder selected = new BooleanBuilder();
        for (MarketSnapshotTime time : times) {
            selected.or(sectorPriceSnapshot
                    .marketType
                    .eq(time.market())
                    .and(sectorPriceSnapshot.snapshotTime.eq(time.snapshotTime())));
        }
        return queryFactory.selectFrom(sectorPriceSnapshot).where(selected).fetch();
    }

    @Override
    public long deleteSnapshotsForDate(LocalDate date, List<MarketSnapshotTime> retainedSnapshotTimes) {
        if (retainedSnapshotTimes.isEmpty()) {
            return 0;
        }
        BooleanExpression targets = null;
        // 시장별 보존 시각이 다를 수 있어 동적 조건을 QueryDSL로 묶는다. 후보 없는 시장은 전체 보존한다.
        for (MarketSnapshotTime retained : retainedSnapshotTimes) {
            BooleanExpression group = sectorPriceSnapshot
                    .marketType
                    .eq(retained.market())
                    .and(sectorPriceSnapshot.snapshotTime.ne(retained.snapshotTime()));
            targets = targets == null ? group : targets.or(group);
        }
        return queryFactory
                .delete(sectorPriceSnapshot)
                .where(
                        sectorPriceSnapshot.snapshotTime.goe(date.atStartOfDay()),
                        sectorPriceSnapshot.snapshotTime.lt(date.plusDays(1).atStartOfDay()),
                        targets)
                .execute();
    }
}
