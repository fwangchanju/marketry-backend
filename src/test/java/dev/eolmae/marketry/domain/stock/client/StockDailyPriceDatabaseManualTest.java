package dev.eolmae.marketry.domain.stock.client;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.eolmae.marketry.common.enums.Country;
import dev.eolmae.marketry.common.enums.Market;
import dev.eolmae.marketry.domain.stock.entity.StockInfo;
import dev.eolmae.marketry.domain.stock.enums.ExchangeType;
import dev.eolmae.marketry.domain.stock.repository.StockInfoRepository;
import jakarta.persistence.EntityManagerFactory;
import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import org.hibernate.SessionFactory;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * API 없이 localhost:15449/daily_price_test 격리 DB에서 V11, QueryDSL 누락 조인, 저장/재실행을 검증한다.
 * <p>DAILY_PRICE_DB_URL=jdbc:postgresql://127.0.0.1:15449/daily_price_test를 지정하고 DB 인증값을 환경변수로 주입한다.
 * .\gradlew.bat manualTest --tests "*.StockDailyPriceDatabaseManualTest" --rerun --info --console=plain
 * 개발/운영 DB를 연결하지 않는다. 검증 데이터는 격리 컨테이너에만 남긴다.
 */
class StockDailyPriceDatabaseManualTest extends StockDailyPriceSpringManualTest {
    @Autowired
    private EntityManagerFactory entityManagerFactory;

    @Autowired
    private PlatformTransactionManager transactionManager;

    @Autowired
    private StockInfoRepository stockInfoRepository;

    @DynamicPropertySource
    static void verificationDatabase(DynamicPropertyRegistry registry) {
        String url = option("DAILY_PRICE_DB_URL", "");
        if ("jdbc:postgresql://127.0.0.1:15449/daily_price_test".equals(url) == false) {
            throw new AssertionError("DB 검증은 localhost:15449/daily_price_test 전용 DB만 연결합니다.");
        }
        registry.add("spring.datasource.url", () -> url);
    }

    @Test
    void 초기_종목과캘린더_읽기의_DB락대기도_5초로_제한한다() {
        assertVerificationDatabase();
        stocks.evict();
        for (String table : List.of("stock_info", "market_calendar")) {
            try (var blocker = entityManagerFactory.unwrap(SessionFactory.class).openSession()) {
                blocker.beginTransaction();
                // PostgreSQL 테이블의 DDL 락은 JPA/QueryDSL로 표현할 수 없어 검증용 DB 고유 구문을 사용한다.
                blocker.createNativeMutationQuery("LOCK TABLE " + table + " IN ACCESS EXCLUSIVE MODE")
                        .executeUpdate();
                Instant started = Instant.now();
                if (table.equals("stock_info")) {
                    assertThatThrownBy(() -> stocks.getCache()).isInstanceOf(RuntimeException.class);
                } else {
                    assertThatThrownBy(() -> calendars.findByCountryAndDate(Country.KR, LocalDate.of(2026, 10, 8)))
                            .isInstanceOf(RuntimeException.class);
                }
                assertThat(Duration.between(started, Instant.now())).isLessThan(Duration.ofSeconds(9));
                blocker.getTransaction().rollback();
            }
        }
    }

    @Test
    void 실제_마이그레이션과_거래소별누락조인_기존값유지를_검증한다() {
        assertVerificationDatabase();
        var transaction = new TransactionTemplate(transactionManager);
        String prefix = java.util.UUID.randomUUID().toString().substring(0, 4).toUpperCase(java.util.Locale.ROOT);
        String krxAndNxt = prefix + "01";
        String krxOnly = prefix + "02";
        String excluded = prefix + "03";
        transaction.executeWithoutResult(status -> {
            stockInfoRepository.saveAll(
                    List.of(stock(krxAndNxt, true, "0"), stock(krxOnly, false, "10"), stock(excluded, true, "8")));
        });
        var date = LocalDate.of(2026, 10, 8);
        var codes = List.of(krxAndNxt, krxOnly, excluded);
        var first = client.parsePrices(
                krxAndNxt,
                ExchangeType.KRX,
                List.of(row("100")),
                date.plusDays(1).atTime(5, 0));
        prices.savePage(first);
        var missing = prices.findMissing(date, codes);
        assertThat(missing)
                .extracting(dev.eolmae.marketry.domain.stock.dto.MissingDailyPriceStock::stockCode)
                .containsExactly(krxAndNxt, krxOnly);
        assertThat(missing.getFirst().krxMissing()).isFalse();
        assertThat(missing.getFirst().nxtMissing()).isTrue();
        assertThat(missing.get(1).nxtMissing()).isFalse();
        var repeated = prices.savePage(client.parsePrices(
                krxAndNxt,
                ExchangeType.KRX,
                List.of(row("110")),
                date.plusDays(1).atTime(5, 0)));
        assertThat(repeated.inserted()).isZero();
        assertThat(repeated.skipped()).isEqualTo(1);
        assertThat(repository
                        .findByStockCodeAndExchangeTypeAndTradeDateBetween(krxAndNxt, ExchangeType.KRX, date, date)
                        .getFirst()
                        .getClosePrice())
                .isEqualByComparingTo("100");
        prices.savePage(client.parsePrices(
                krxAndNxt,
                ExchangeType.NXT,
                List.of(row("105")),
                date.plusDays(1).atTime(5, 0)));
        prices.savePage(client.parsePrices(
                krxOnly, ExchangeType.KRX, List.of(row("90")), date.plusDays(1).atTime(5, 0)));
        assertThat(prices.findMissing(date, codes)).isEmpty();
        System.out.println("VERIFIED: V11, KRX/NXT persistence, missing-only QueryDSL join, existing price retention");
    }

    private void assertVerificationDatabase() {
        assertThat(option("DAILY_PRICE_DB_URL", ""))
                .as("검증 전용 DB를 명시해야 합니다.")
                .isEqualTo("jdbc:postgresql://127.0.0.1:15449/daily_price_test");
    }

    private StockInfo stock(String code, boolean nxt, String marketCode) {
        return StockInfo.create(code, "검증종목", Market.KOSPI, marketCode, null, 100L, BigDecimal.TEN, nxt);
    }

    private Map<String, Object> row(String close) {
        return Map.of(
                "dt",
                "20261008",
                "open_pric",
                "100",
                "high_pric",
                "110",
                "low_pric",
                "90",
                "cur_prc",
                close,
                "trde_qty",
                "1000");
    }
}
