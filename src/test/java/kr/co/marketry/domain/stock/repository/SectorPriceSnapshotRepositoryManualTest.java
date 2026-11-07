package kr.co.marketry.domain.stock.repository;

import static org.assertj.core.api.Assertions.assertThat;

import com.querydsl.jpa.impl.JPAQueryFactory;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import kr.co.marketry.common.enums.Country;
import kr.co.marketry.common.enums.Market;
import kr.co.marketry.domain.stock.entity.MarketCalendar;
import kr.co.marketry.domain.stock.entity.SectorPriceSnapshot;
import kr.co.marketry.domain.stock.enums.ExchangeType;
import kr.co.marketry.domain.stock.enums.MarketCalendarStatus;
import kr.co.marketry.domain.stock.repository.SectorPriceSnapshotRepositoryCustom.MarketSnapshotTime;
import kr.co.marketry.domain.stock.repository.SectorPriceSnapshotRepositoryCustom.TimeWindow;
import org.hibernate.Session;
import org.hibernate.boot.MetadataSources;
import org.hibernate.boot.registry.StandardServiceRegistryBuilder;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

/**
 * API 호출 없이 QueryDSL을 실제 PostgreSQL에서 검증한다. localhost:15439의 snapshot_query_test 전용 DB만 사용한다.
 * 테스트용 PostgreSQL 17에 snapshot_test 사용자/비밀번호를 설정한 뒤 ./gradlew.bat snapshotQueryTest로 실행한다.
 * 이 DB의 두 검증 테이블을 생성하고 종료 때 제거하므로 운영 DB나 개발 데이터 DB를 연결하지 않는다.
 */
@Tag("manual")
class SectorPriceSnapshotRepositoryManualTest {
    private static final LocalDate FIRST = LocalDate.of(2026, 10, 1);

    @Test
    void 달력_확인에_실패해도_가격자료가_있는_날짜를_최신_자료로_조회한다() {
        var registry = new StandardServiceRegistryBuilder()
                .applySettings(Map.of(
                        "jakarta.persistence.jdbc.url", "jdbc:postgresql://localhost:15439/snapshot_query_test",
                        "jakarta.persistence.jdbc.user", "snapshot_test",
                        "jakarta.persistence.jdbc.password", "snapshot_test",
                        "hibernate.hbm2ddl.auto", "create-drop",
                        "hibernate.physical_naming_strategy",
                                "org.hibernate.boot.model.naming.PhysicalNamingStrategySnakeCaseImpl"))
                .build();
        try (var factory = new MetadataSources(registry)
                        .addAnnotatedClass(MarketCalendar.class)
                        .addAnnotatedClass(SectorPriceSnapshot.class)
                        .buildMetadata()
                        .buildSessionFactory();
                var session = factory.openSession()) {
            session.beginTransaction();
            LocalDate second = FIRST.plusDays(1);
            LocalDate soleAfter = FIRST.plusDays(4);
            LocalDate noCandidate = FIRST.plusDays(5);
            LocalDate latest = FIRST.plusDays(6);
            LocalDate today = FIRST.plusDays(7);
            for (LocalDate date : List.of(FIRST, second, soleAfter, noCandidate, latest, today, FIRST.plusDays(11))) {
                MarketCalendarStatus status =
                        date.equals(today) ? MarketCalendarStatus.FAILED : MarketCalendarStatus.TRADING_DAY;
                session.persist(MarketCalendar.create(Country.KR, date, status, null));
            }
            session.persist(MarketCalendar.create(Country.KR, FIRST.plusDays(2), MarketCalendarStatus.HOLIDAY, null));
            session.persist(MarketCalendar.create(Country.KR, FIRST.plusDays(3), MarketCalendarStatus.FAILED, null));
            for (Market market : Market.values()) {
                for (int minute : List.of(30, 35)) {
                    price(session, market, FIRST.atTime(15, minute), "one");
                }
                price(session, market, FIRST.atTime(20, 0), "one");
                price(session, market, FIRST.atTime(16, 40), "one");
                price(session, market, second.atTime(15, 35), "one");
                price(session, market, second.atTime(15, 35), "two");
                price(session, market, soleAfter.atTime(17, 0), "one");
                price(session, market, noCandidate.atTime(17, 0), "one");
                price(session, market, noCandidate.atTime(20, 0), "one");
                for (LocalDate excluded :
                        List.of(FIRST.minusDays(1), FIRST.plusDays(2), FIRST.plusDays(3), FIRST.plusDays(11))) {
                    price(session, market, excluded.atTime(20, 0), "one");
                }
            }
            price(session, Market.KOSPI, latest.atTime(20, 0), "one");
            price(session, Market.KOSDAQ, latest.atTime(19, 55), "one");
            session.flush();
            var repository = new SectorPriceSnapshotRepositoryImpl(new JPAQueryFactory(session));
            assertThat(repository.findTradingSnapshotDates(today))
                    .containsExactly(today, latest, noCandidate, soleAfter, second, FIRST);
            assertThat(repository.findLatestCommonSnapshotTime(List.of(Market.KOSPI), today))
                    .contains(latest.atTime(20, 0));
            assertThat(repository.findLatestCommonSnapshotTime(List.of(Market.KOSPI, Market.KOSDAQ), today))
                    .contains(noCandidate.atTime(20, 0));
            assertThat(repository.findMultipleSnapshotDates(second)).containsExactly(FIRST);

            var summaries = repository.findSnapshotDaySummaries(List.of(
                    new TimeWindow(FIRST.atStartOfDay(), FIRST.atTime(16, 40)),
                    new TimeWindow(second.atStartOfDay(), second.atTime(16, 40)),
                    new TimeWindow(soleAfter.atStartOfDay(), soleAfter.atTime(16, 40)),
                    new TimeWindow(noCandidate.atStartOfDay(), noCandidate.atTime(16, 40))));
            var first = summaries.stream()
                    .filter(row -> row.latestTime().toLocalDate().equals(FIRST))
                    .toList();
            assertThat(summaries).hasSize(8);
            assertThat(first).hasSize(2);
            assertThat(first).allSatisfy(row -> {
                assertThat(row.distinctTimeCount()).isEqualTo(4);
                assertThat(row.closingSnapshot()).contains(new MarketSnapshotTime(row.market(), FIRST.atTime(15, 35)));
            });
            assertThat(summaries.stream()
                            .filter(row -> row.latestTime().toLocalDate().equals(second)))
                    .allSatisfy(row -> assertThat(row.distinctTimeCount()).isEqualTo(1));
            assertThat(summaries.stream()
                            .filter(row -> row.latestTime().toLocalDate().equals(soleAfter)))
                    .allSatisfy(row -> assertThat(row.closingSnapshot())
                            .contains(new MarketSnapshotTime(row.market(), soleAfter.atTime(17, 0))));
            assertThat(summaries.stream()
                            .filter(row -> row.latestTime().toLocalDate().equals(noCandidate)))
                    .allSatisfy(row -> assertThat(row.closingSnapshot()).isEmpty());
            var retained = first.stream()
                    .flatMap(row -> row.closingSnapshot().stream())
                    .toList();
            assertThat(repository.findByMarketSnapshotTimes(retained)).hasSize(2);
            assertThat(repository.deleteSnapshotsForDate(FIRST, retained)).isEqualTo(6);
            assertThat(repository.findMultipleSnapshotDates(second)).isEmpty();
            assertThat(repository.findByMarketSnapshotTimes(
                            List.of(new MarketSnapshotTime(Market.KOSPI, second.atTime(15, 35)))))
                    .hasSize(2);
            assertThat(repository.deleteSnapshotsForDate(noCandidate, List.of()))
                    .isZero();
            price(session, Market.KOSPI, today.atStartOfDay(), "today");
            price(session, Market.KOSDAQ, today.atStartOfDay(), "today");
            price(session, Market.KOSPI, today.atTime(20, 0), "today");
            price(session, Market.KOSDAQ, today.atTime(20, 0), "today");
            price(session, Market.KOSPI, today.atTime(20, 5), "today");
            session.flush();
            assertThat(repository.findLatestCommonSnapshotTime(List.of(Market.KOSPI), today))
                    .contains(today.atTime(20, 5));
            assertThat(repository.findLatestCommonSnapshotTime(List.of(Market.KOSPI, Market.KOSDAQ), today))
                    .contains(today.atTime(20, 0));
            session.getTransaction().rollback();
        } finally {
            StandardServiceRegistryBuilder.destroy(registry);
        }
    }

    private void price(Session session, Market market, LocalDateTime time, String code) {
        session.persist(SectorPriceSnapshot.create(
                market, time, code, ExchangeType.SOR, code, BigDecimal.TEN, BigDecimal.ZERO, BigDecimal.ZERO));
    }
}
