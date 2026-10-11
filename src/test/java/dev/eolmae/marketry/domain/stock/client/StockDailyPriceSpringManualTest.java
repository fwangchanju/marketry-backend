package dev.eolmae.marketry.domain.stock.client;

import dev.eolmae.marketry.MarketryApplication;
import dev.eolmae.marketry.domain.stock.collector.StockDailyPriceCollector;
import dev.eolmae.marketry.domain.stock.repository.StockDailyPriceRepository;
import dev.eolmae.marketry.domain.stock.service.MarketCalendarService;
import dev.eolmae.marketry.domain.stock.service.StockDailyPriceService;
import dev.eolmae.marketry.domain.stock.service.StockInfoCacheService;
import java.util.Arrays;
import java.util.List;
import org.junit.jupiter.api.Tag;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

/** 앱의 빈·설정·트랜잭션을 그대로 사용한다. 인증값은 실행 스크립트가 환경변수로 주입한다. */
@Tag("manual")
@ActiveProfiles({"prod", "local"})
@SpringBootTest(
        classes = MarketryApplication.class,
        webEnvironment = SpringBootTest.WebEnvironment.MOCK,
        properties = {
            "scheduling.enabled=false",
            "renderer.owner-capture-enabled=false",
            "auth.google.client-id=unused.apps.googleusercontent.com",
            "auth.google.client-secret=unused",
            "auth.jwt-secret=verification-only-01234567890123456789012345678901234567890123456789",
            "krx.login-id=unused",
            "krx.login-pw=unused",
            "telegram.bot-token=unused",
            "telegram.chat-id=unused",
            "telegram.developer-chat-id=",
            "kiwoom.proxy-host="
        })
abstract class StockDailyPriceSpringManualTest {
    @Autowired
    protected StockDailyPriceClient client;

    @Autowired
    protected StockDailyPriceService prices;

    @Autowired
    protected StockInfoCacheService stocks;

    @Autowired
    protected MarketCalendarService calendars;

    @Autowired
    protected StockDailyPriceRepository repository;

    @Autowired
    protected StockDailyPriceCollector collector;

    @DynamicPropertySource
    static void localDatabase(DynamicPropertyRegistry registry) {
        String url = option("DAILY_PRICE_DB_URL", "jdbc:postgresql://127.0.0.1:15432/market_monitor_db");
        if (url.matches("jdbc:postgresql://(127\\.0\\.0\\.1|localhost):[0-9]+/[a-z_]+") == false) {
            throw new AssertionError("수동 적재 테스트는 로컬 DB만 연결할 수 있습니다.");
        }
        registry.add("spring.datasource.url", () -> url);
    }

    static String option(String name, String fallback) {
        String value = System.getenv(name);
        return value == null || value.isBlank() ? fallback : value;
    }

    static List<String> selectedCodes() {
        String symbols = option("DAILY_PRICE_SYMBOLS", "ALL");
        if ("ALL".equals(symbols)) {
            return List.of();
        }
        var codes = Arrays.stream(symbols.split(",", -1))
                .map(String::strip)
                .distinct()
                .toList();
        if (codes.stream().anyMatch(code -> code.matches("[0-9A-Z]{6}") == false)) {
            throw new AssertionError("DAILY_PRICE_SYMBOLS는 ALL 또는 접미사 없는 종목코드 목록입니다.");
        }
        return codes;
    }
}
