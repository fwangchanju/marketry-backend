package dev.eolmae.marketry.domain.stock.repository;

import static org.assertj.core.api.Assertions.assertThat;

import com.querydsl.jpa.impl.JPAQueryFactory;
import dev.eolmae.marketry.domain.stock.entity.MarketCalendar;
import dev.eolmae.marketry.domain.stock.entity.StockDailyPrice;
import dev.eolmae.marketry.domain.stock.enums.ExchangeType;
import dev.eolmae.marketry.domain.stock.service.StockDailyPriceService;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import org.hibernate.boot.MetadataSources;
import org.hibernate.boot.registry.StandardServiceRegistryBuilder;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.data.jpa.repository.support.JpaRepositoryFactory;
import org.springframework.data.repository.core.support.RepositoryComposition.RepositoryFragments;

/**
 * 일봉 조회 쿼리가 Spring Data 파생 메서드·QueryDSL로 만들어지고 의도한 SQL로 실행되는지 확인한다.
 * <p>운영·사용자 로컬 DB가 아닌 별도 컨테이너(localhost:15450/explain_test, 가짜 계정)에 market_calendar와 stock_daily_price를
 * 복사해 두고 {@code ./gradlew.bat manualTest --tests "*StockDailyPriceQueryManualTest" -i}로 실행한다. 읽기 전용이며 생성된 SQL은
 * Hibernate 로그(show_sql)로 확인해 같은 DB에서 EXPLAIN으로 비교한다.
 */
@Tag("manual")
class StockDailyPriceQueryManualTest {
    private static final LocalDate DATE = LocalDate.of(2026, 10, 8);

    @Test
    void 일봉_존재_종가_월범위_활성화_쿼리가_실제_데이터에서_동작한다() {
        var registry = new StandardServiceRegistryBuilder()
                .applySettings(Map.of(
                        "jakarta.persistence.jdbc.url", "jdbc:postgresql://localhost:15450/explain_test",
                        "jakarta.persistence.jdbc.user", "explain_test",
                        "jakarta.persistence.jdbc.password", "explain_test",
                        "hibernate.hbm2ddl.auto", "none",
                        "hibernate.show_sql", "true",
                        "hibernate.format_sql", "true",
                        "hibernate.physical_naming_strategy",
                                "org.hibernate.boot.model.naming.PhysicalNamingStrategySnakeCaseImpl"))
                .build();
        try (var factory = new MetadataSources(registry)
                        .addAnnotatedClass(MarketCalendar.class)
                        .addAnnotatedClass(StockDailyPrice.class)
                        .buildMetadata()
                        .buildSessionFactory();
                var session = factory.openSession()) {
            session.setDefaultReadOnly(true);
            session.beginTransaction();
            var custom = new StockDailyPriceRepositoryImpl(new JPAQueryFactory(session));
            var repository = new JpaRepositoryFactory(session)
                    .getRepository(StockDailyPriceRepository.class, RepositoryFragments.just(custom));
            var service = new StockDailyPriceService(repository);

            assertThat(service.existsKrxPrice(DATE)).isTrue();
            assertThat(service.existsKrxPrice(LocalDate.of(2026, 10, 10))).isFalse();
            assertThat(service.findKrxClosePrices(DATE))
                    .hasSizeGreaterThan(2000)
                    .containsKey("005930");
            assertThat(repository.findCalendarDatesWithPrice(
                            ExchangeType.KRX, LocalDate.of(2026, 10, 1), LocalDate.of(2026, 10, 31)))
                    .contains(DATE);
            assertThat(service.findKrxPriceDates(List.of(DATE, LocalDate.of(2026, 10, 10))))
                    .containsExactly(DATE);
            session.getTransaction().rollback();
        } finally {
            StandardServiceRegistryBuilder.destroy(registry);
        }
    }
}
