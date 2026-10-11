package dev.eolmae.marketry.domain.stock.client;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

import dev.eolmae.marketry.common.enums.Country;
import dev.eolmae.marketry.common.enums.Market;
import dev.eolmae.marketry.common.enums.Zone;
import dev.eolmae.marketry.domain.stock.entity.MarketCalendar;
import dev.eolmae.marketry.domain.stock.entity.StockInfo;
import dev.eolmae.marketry.domain.stock.enums.ExchangeType;
import dev.eolmae.marketry.domain.stock.enums.MarketCalendarStatus;
import dev.eolmae.marketry.domain.stock.repository.StockInfoRepository;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.aop.support.AopUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

/**
 * 인증 파일·외부 API 없이 격리 DB에서 Spring 수동 적재 테스트의 주입·페이지별 커밋·재실행을 검증한다.
 * <p>DAILY_PRICE_DB_URL=jdbc:postgresql://127.0.0.1:15449/daily_price_test와 가짜 인증 환경변수가 필요하다.
 * .\gradlew.bat manualTest --tests "*.StockDailyPriceApplicationManualTest" --rerun --info --console=plain
 */
@Import(StockDailyPriceBackfillManualTest.BackfillConfiguration.class)
class StockDailyPriceApplicationManualTest extends StockDailyPriceSpringManualTest {
    @MockitoBean
    private KiwoomApiClient apiClient;

    @Autowired
    private StockInfoRepository stockInfoRepository;

    @Autowired
    private ConfigurableApplicationContext context;

    @DynamicPropertySource
    static void verificationDatabase(DynamicPropertyRegistry registry) {
        String url = option("DAILY_PRICE_DB_URL", "");
        if ("jdbc:postgresql://127.0.0.1:15449/daily_price_test".equals(url) == false) {
            throw new AssertionError("Spring 검증은 localhost:15449/daily_price_test 전용 DB만 연결합니다.");
        }
        registry.add("spring.datasource.url", () -> url);
    }

    @Test
    void 수동_적재도_앱의_빈과_실제_트랜잭션을_사용한다() throws InterruptedException {
        LocalDate today = LocalDate.now(Zone.KST.zoneId());
        String code = java.util.UUID.randomUUID().toString().substring(0, 6).toUpperCase(java.util.Locale.ROOT);
        stockInfoRepository.save(
                StockInfo.create(code, "Spring 검증종목", Market.KOSPI, "0", null, 100L, BigDecimal.TEN, true));
        stocks.evict();
        when(apiClient.postFirstPage(eq("ka11081"), eq("/api/dostk/chart"), anyMap()))
                .thenReturn(
                        Map.of("result_list", List.of(row(today), row(today.minusDays(1)), row(today.minusDays(2)))));

        assertThat(AopUtils.isAopProxy(prices)).as("앱의 트랜잭션 프록시").isTrue();
        assertThat(context.containsBean("org.springframework.context.annotation.internalScheduledAnnotationProcessor"))
                .as("자동 스케줄 비활성화")
                .isFalse();
        var backfill = context.getAutowireCapableBeanFactory().createBean(StockDailyPriceBackfillManualTest.class);
        backfill.runBackfill(today, List.of(code));
        for (ExchangeType exchange : List.of(ExchangeType.KRX, ExchangeType.NXT)) {
            assertThat(repository.findByStockCodeAndExchangeTypeAndTradeDateBetween(
                            code, exchange, today.minusDays(2), today))
                    .extracting(dev.eolmae.marketry.domain.stock.entity.StockDailyPrice::getTradeDate)
                    .containsExactlyInAnyOrder(today.minusDays(1), today.minusDays(2));
        }
        backfill.runBackfill(today, List.of(code));
        calendars.saveSuccess(
                Country.KR,
                List.of(MarketCalendar.create(Country.KR, today.minusDays(1), MarketCalendarStatus.TRADING_DAY, null)));
        var manual = context.getAutowireCapableBeanFactory().createBean(StockDailyPriceManualTest.class);
        manual.runCollector(today.minusDays(1), List.of(code));
        System.out.println(
                "VERIFIED: Spring manual backfill, committed KRX/NXT rows, existing row retention, production collector injection");
    }

    private Map<String, Object> row(LocalDate date) {
        return Map.of(
                "dt",
                date.format(DateTimeFormatter.BASIC_ISO_DATE),
                "open_pric",
                "100",
                "high_pric",
                "110",
                "low_pric",
                "90",
                "cur_prc",
                "100",
                "trde_qty",
                "1000");
    }
}
