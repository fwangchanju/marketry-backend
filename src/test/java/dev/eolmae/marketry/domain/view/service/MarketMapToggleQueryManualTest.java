package dev.eolmae.marketry.domain.view.service;

import static dev.eolmae.marketry.domain.stock.entity.QMarketCalendar.marketCalendar;
import static dev.eolmae.marketry.domain.stock.entity.QSectorPriceSnapshot.sectorPriceSnapshot;
import static org.assertj.core.api.Assertions.assertThat;

import com.querydsl.core.BooleanBuilder;
import com.querydsl.core.types.dsl.CaseBuilder;
import com.querydsl.core.types.dsl.Expressions;
import com.querydsl.jpa.JPAExpressions;
import com.querydsl.jpa.impl.JPAQueryFactory;
import dev.eolmae.marketry.common.enums.Country;
import dev.eolmae.marketry.common.enums.Market;
import dev.eolmae.marketry.domain.stock.entity.MarketCalendar;
import dev.eolmae.marketry.domain.stock.entity.SectorPriceSnapshot;
import dev.eolmae.marketry.domain.stock.enums.MarketCalendarStatus;
import dev.eolmae.marketry.domain.stock.properties.MarketHoursProperties;
import dev.eolmae.marketry.domain.stock.repository.MarketCalendarRepository;
import dev.eolmae.marketry.domain.stock.repository.SectorPriceSnapshotRepository;
import dev.eolmae.marketry.domain.stock.repository.SectorPriceSnapshotRepositoryImpl;
import dev.eolmae.marketry.domain.stock.service.ClosingPriceCacheService;
import dev.eolmae.marketry.domain.stock.service.MarketCalendarService;
import dev.eolmae.marketry.domain.stock.service.MarketCalendarTimeService;
import dev.eolmae.marketry.domain.stock.service.SectorPriceCacheService;
import dev.eolmae.marketry.domain.stock.service.SectorPriceCacheService.CachedStockPrice;
import dev.eolmae.marketry.domain.stock.service.SectorPriceSnapshotService;
import dev.eolmae.marketry.domain.stock.service.StockDailyPriceService;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.YearMonth;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.hibernate.Session;
import org.hibernate.boot.MetadataSources;
import org.hibernate.boot.registry.StandardServiceRegistryBuilder;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.data.jpa.repository.support.JpaRepositoryFactory;
import org.springframework.data.repository.core.support.RepositoryComposition.RepositoryFragments;

/**
 * localhost:15439/snapshot_query_test에 로컬 가격·캘린더 두 테이블을 복사한 뒤
 * ./gradlew.bat manualTest --tests "*MarketMapToggleQueryManualTest" -i로 실행한다.
 * API·스케줄러 없이 실제 가격 서비스와 QueryDSL을 실행한다. DDL 없이 읽기 전용 트랜잭션을 사용한다.
 * 신규 JVM/캐시 미적중 상태의 가격 조회 경로이며 전체 HTTP 응답 시간은 측정하지 않는다.
 */
@Tag("manual")
class MarketMapToggleQueryManualTest {
    private static final LocalDate TODAY = LocalDate.of(2026, 10, 10);
    private static final List<Market> MARKETS = List.of(Market.KOSPI, Market.KOSDAQ);

    @Test
    void 종가_누적_별도의_실제_가격_조회_순서와_수정_전후_결과를_비교한다() {
        var registry = new StandardServiceRegistryBuilder()
                .applySettings(Map.of(
                        "jakarta.persistence.jdbc.url", "jdbc:postgresql://localhost:15439/snapshot_query_test",
                        "jakarta.persistence.jdbc.user", "snapshot_test",
                        "jakarta.persistence.jdbc.password", "snapshot_test",
                        "hibernate.hbm2ddl.auto", "none",
                        "hibernate.physical_naming_strategy",
                                "org.hibernate.boot.model.naming.PhysicalNamingStrategySnakeCaseImpl"))
                .build();
        try (var factory = new MetadataSources(registry)
                        .addAnnotatedClass(MarketCalendar.class)
                        .addAnnotatedClass(SectorPriceSnapshot.class)
                        .buildMetadata()
                        .buildSessionFactory();
                var session = factory.openSession()) {
            session.setDefaultReadOnly(true);
            session.doWork(connection -> connection.setReadOnly(true));
            session.beginTransaction();
            for (String toggle : List.of("close", "daily", "afterHours")) {
                for (int round = 1; round <= 3; round++) {
                    var before = run(session, toggle, round, true);
                    var after = run(session, toggle, round, false);
                    assertThat(after.time()).isEqualTo(before.time());
                    assertThat(after.prices()).isEqualTo(before.prices()).isNotEmpty();
                    assertThat(after.closingTimes()).isEqualTo(before.closingTimes());
                }
            }
            session.getTransaction().rollback();
        } finally {
            StandardServiceRegistryBuilder.destroy(registry);
        }
    }

    private Result run(Session session, String toggle, int round, boolean before) {
        session.clear();
        var queryFactory = new JPAQueryFactory(session);
        var custom = before ? new BeforeRepository(queryFactory) : new SectorPriceSnapshotRepositoryImpl(queryFactory);
        var repositoryFactory = new JpaRepositoryFactory(session);
        var repository =
                repositoryFactory.getRepository(SectorPriceSnapshotRepository.class, RepositoryFragments.just(custom));
        var calendars = repositoryFactory.getRepository(MarketCalendarRepository.class);
        var timeService = new MarketCalendarTimeService(
                new MarketCalendarService(calendars, event -> {}),
                new MarketHoursProperties(LocalTime.of(15, 30), LocalTime.of(15, 40)));
        var snapshots = new SectorPriceSnapshotService(
                repository, timeService, org.mockito.Mockito.mock(StockDailyPriceService.class));
        var prices = new SectorPriceCacheService(repository);
        var closing = new ClosingPriceCacheService(repository, timeService, snapshots);
        String label = (before ? "BEFORE" : "AFTER") + "/" + toggle + "/" + round;
        long started = System.nanoTime();
        long step = started;
        LocalDateTime time = custom.findLatestCommonSnapshotTime(MARKETS, TODAY).orElseThrow();
        printStep(label, "latestTradingDay+latest", step);
        step = System.nanoTime();
        Map<String, CachedStockPrice> values = load(prices, time);
        Map<LocalDate, LocalDateTime> closingTimes = Map.of();
        printStep(label, "latestPrices", step);
        if (toggle.equals("close")) {
            step = System.nanoTime();
            closingTimes = snapshots.findClosingSnapshotTimes(MARKETS, YearMonth.from(time));
            time = closingTimes.get(time.toLocalDate());
            assertThat(time).isNotNull();
            printStep(label, "monthCalendar+closingTimes", step);
            step = System.nanoTime();
            for (Market market : MARKETS) {
                assertThat(snapshots.existsSnapshot(market, time)).isTrue();
            }
            values = load(prices, time);
            printStep(label, "selectedTimeExists+closingPrices", step);
        } else if (toggle.equals("afterHours")) {
            step = System.nanoTime();
            var times = timeService.resolve(time.toLocalDate());
            assertThat(time.isBefore(times.closingWindowEnd())).isFalse();
            values = AfterHoursChangeRates.apply(values, closing.getFor(time.toLocalDate(), TODAY.atTime(20, 0)));
            printStep(label, "dayCalendar+closingSelection+basePrices+rates", step);
        }
        step = System.nanoTime();
        timeService.resolve(time.toLocalDate());
        printStep(label, "responseAfterHoursStart", step);
        System.out.printf("TOTAL %s %.3fms time=%s stocks=%d%n", label, milliseconds(started), time, values.size());
        return new Result(time, values, closingTimes);
    }

    private Map<String, CachedStockPrice> load(SectorPriceCacheService prices, LocalDateTime time) {
        Map<String, CachedStockPrice> loaded = new HashMap<>();
        for (Market market : MARKETS) {
            loaded.putAll(prices.getCache(market, time));
        }
        return loaded;
    }

    private void printStep(String label, String step, long started) {
        System.out.printf("STEP %s %s %.3fms%n", label, step, milliseconds(started));
    }

    private double milliseconds(long started) {
        return (System.nanoTime() - started) / 1_000_000.0;
    }

    private record Result(
            LocalDateTime time, Map<String, CachedStockPrice> prices, Map<LocalDate, LocalDateTime> closingTimes) {}

    /** 직전 배포와 동일한 QueryDSL을 비교군으로만 보존한다. */
    private static class BeforeRepository extends SectorPriceSnapshotRepositoryImpl {
        private final JPAQueryFactory queryFactory;

        BeforeRepository(JPAQueryFactory queryFactory) {
            super(queryFactory);
            this.queryFactory = queryFactory;
        }

        @Override
        public List<SnapshotDaySummary> findSnapshotDaySummaries(List<TimeWindow> windows) {
            if (windows.isEmpty()) {
                return List.of();
            }
            BooleanBuilder dates = new BooleanBuilder();
            BooleanBuilder beforeAfterHours = new BooleanBuilder();
            for (TimeWindow window : windows) {
                LocalDateTime start = window.from().toLocalDate().atStartOfDay();
                var inDay = sectorPriceSnapshot
                        .snapshotTime
                        .goe(start)
                        .and(sectorPriceSnapshot.snapshotTime.lt(start.plusDays(1)));
                dates.or(inDay);
                beforeAfterHours.or(inDay.and(sectorPriceSnapshot.snapshotTime.lt(window.toExclusive())));
            }
            var latest = sectorPriceSnapshot.snapshotTime.max();
            var count = sectorPriceSnapshot.snapshotTime.countDistinct();
            var before = new CaseBuilder()
                    .when(beforeAfterHours)
                    .then(sectorPriceSnapshot.snapshotTime)
                    .otherwise(Expressions.nullExpression(LocalDateTime.class))
                    .max();
            return queryFactory
                    .select(sectorPriceSnapshot.marketType, latest, count, before)
                    .from(sectorPriceSnapshot)
                    .where(dates)
                    .groupBy(
                            sectorPriceSnapshot.marketType,
                            sectorPriceSnapshot.snapshotTime.year(),
                            sectorPriceSnapshot.snapshotTime.month(),
                            sectorPriceSnapshot.snapshotTime.dayOfMonth())
                    .fetch()
                    .stream()
                    .map(tuple -> new SnapshotDaySummary(
                            tuple.get(sectorPriceSnapshot.marketType),
                            tuple.get(latest),
                            tuple.get(count),
                            tuple.get(before)))
                    .toList();
        }

        @Override
        public Optional<LocalDateTime> findLatestCommonSnapshotTime(List<Market> markets, LocalDate today) {
            return Optional.ofNullable(queryFactory
                    .select(sectorPriceSnapshot.snapshotTime)
                    .from(sectorPriceSnapshot)
                    .where(
                            sectorPriceSnapshot.marketType.in(markets),
                            sectorPriceSnapshot.snapshotTime.lt(
                                    today.plusDays(1).atStartOfDay()),
                            JPAExpressions.selectOne()
                                    .from(marketCalendar)
                                    .where(
                                            marketCalendar.country.eq(Country.KR),
                                            marketCalendar.status.eq(MarketCalendarStatus.TRADING_DAY),
                                            marketCalendar.date.year().eq(sectorPriceSnapshot.snapshotTime.year()),
                                            marketCalendar.date.month().eq(sectorPriceSnapshot.snapshotTime.month()),
                                            marketCalendar
                                                    .date
                                                    .dayOfMonth()
                                                    .eq(sectorPriceSnapshot.snapshotTime.dayOfMonth()))
                                    .exists())
                    .groupBy(sectorPriceSnapshot.snapshotTime)
                    .having(sectorPriceSnapshot.marketType.countDistinct().eq((long) markets.size()))
                    .orderBy(sectorPriceSnapshot.snapshotTime.desc())
                    .limit(1)
                    .fetchOne());
        }
    }
}
