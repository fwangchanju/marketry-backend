package dev.eolmae.marketry.domain.stock.collector;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneId;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;

/**
 * 전체 수집기 한 사이클 실행 — 로컬 DB 적재 후 화면/수치 검증용
 *
 * 실행 조건: KIWOOM_APP_KEY, KIWOOM_SECRET, DB_APP_PASSWD 환경변수 필요
 * 실행 명령: ./gradlew manualTest --tests "*.FullDataCollectionTest" -i
 *
 * snapshotTime: 직전 거래일 날짜 + 현재 시각 정각 내림 (스케줄러와 동일한 방식)
 */
@Slf4j
@Tag("manual")
@SpringBootTest
@ActiveProfiles("prod")
@TestPropertySource(
        properties = {
            "spring.test.database.replace=none",
            "spring.datasource.url=jdbc:postgresql://localhost:5433/market_monitor_db",
            "spring.flyway.enabled=false"
        })
class FullDataCollectionTest {

    private static final ZoneId KST = ZoneId.of("Asia/Seoul");

    @Autowired
    StockInfoCollector stockInfoCollector;

    @Autowired
    IntradayInvestorRankingCollector intradayInvestorRankingCollector;

    @Autowired
    ProgramTradeIntradayCollector programTradeIntradayCollector;

    @Autowired
    ProgramTradeDailyCollector programTradeDailyCollector;

    @Autowired
    ShortSellingTrendCollector shortSellingTrendCollector;

    @Autowired
    IndexContributionRankingCollector indexContributionRankingCollector;

    @Autowired
    JdbcTemplate jdbcTemplate;

    @Test
    void 전체수집_한사이클() {
        LocalDate tradeDate = lastTradingDay();
        // 스케줄러와 동일한 방식: 현재 시각 정각 내림 + 직전 거래일 날짜
        // (ProgramTradingCollector는 날짜를 API에 전달, 나머지는 DB 타임슬롯으로만 사용)
        LocalDateTime snapshotTime = tradeDate.atTime(LocalTime.of(20, 0));

        log.info("=== 전체 수집 시작 | tradeDate={} | snapshotTime={} ===", tradeDate, snapshotTime);

        //        run("종목마스터",          () -> stockMasterCollector.sync());
        //        setupSamsungWatchStock();
        //        run("시장개요",            () -> marketOverviewCollector.collect(snapshotTime));
        //        run("투자자매매종합",       () -> investorTradingSummaryCollector.collect(snapshotTime));
        //        run("장중투자자랭킹",       () -> intradayInvestorRankingCollector.collect(snapshotTime));
        //        run("프로그램매매랭킹",     () -> programTradingRankingCollector.collect(snapshotTime));
        //        run("지수기여도랭킹",       () -> indexContributionRankingCollector.collect(snapshotTime));
        //        run("프로그램매매일별",     () -> programTradingCollector.collectDaily(tradeDate));
        //        run("프로그램매매장중",     () -> programTradingCollector.collect(snapshotTime));
        run("공매도", () -> shortSellingTrendCollector.collect(snapshotTime));

        log.info("=== 전체 수집 완료 ===");
    }

    /** default 유저 + 삼성전자(005930) 관심종목(is_primary=true) 준비. 이미 있으면 스킵. */
    private void setupSamsungWatchStock() {
        jdbcTemplate.update("""
            INSERT INTO app_user (user_key, display_name, timezone, created_at, updated_at)
            VALUES ('default', '관리자', 'Asia/Seoul', NOW(), NOW())
            ON CONFLICT (user_key) DO NOTHING
            """);
        jdbcTemplate.update("""
            INSERT INTO watch_stock (user_id, stock_code, is_primary, register_by, created_at)
            SELECT u.id, '005930', true, 'USER', NOW()
            FROM app_user u
            WHERE u.user_key = 'default'
            ON CONFLICT (user_id, stock_code) DO NOTHING
            """);
        log.info("[setup] default 유저 + 삼성전자(005930) 관심종목 준비 완료 (이미 있으면 스킵)");
    }

    private LocalDate lastTradingDay() {
        LocalDate today = LocalDate.now(KST);
        return switch (today.getDayOfWeek()) {
            case SATURDAY -> today.minusDays(1);
            case SUNDAY -> today.minusDays(2);
            default -> today;
        };
    }

    private void run(String name, Runnable task) {
        try {
            task.run();
            log.info("[OK] {}", name);
        } catch (Exception e) {
            log.error("[FAIL] {} — {}", name, e.getMessage(), e);
        }
    }
}
