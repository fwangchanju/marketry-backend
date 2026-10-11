package dev.eolmae.marketry.domain.stock.client;

import static org.assertj.core.api.Assertions.assertThat;

import dev.eolmae.marketry.domain.stock.collector.StockDailyPriceCollector;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * Spring 빈으로 등록된 운영 수집기를 호출한다. local DB만 연결하고 스케줄러는 비활성화한다.
 * <p>DAILY_PRICE_DATE=2026-10-08(필수), DAILY_PRICE_SYMBOLS=005930,000660 또는 ALL(기본).
 * .\gradlew.bat manualTest --tests "*.StockDailyPriceManualTest" --rerun --info --console=plain
 * <p>앱과 동일하게 POSTGRES_PASSWORD, KIWOOM_APP_KEY_B, KIWOOM_SECRET_B를 환경변수로 전달한다.
 * 대상일이 거래일일 때만(HOLIDAY 제외, FAILED·없음은 요일 기준) 실패한 종목·거래소를 최대3회 수집한다.
 */
class StockDailyPriceManualTest extends StockDailyPriceSpringManualTest {
    @Test
    void 지정날짜와종목의_운영수집기를_로컬DB에_실행한다() {
        LocalDate date = LocalDate.parse(option("DAILY_PRICE_DATE", ""));
        runCollector(date, selectedCodes());
    }

    void runCollector(LocalDate date, List<String> selectedCodes) {
        var result = collector.collect(date, selectedCodes, Instant.now().plus(Duration.ofMinutes(50)));
        System.out.printf(
                "COMPLETE: date=%s status=%s passes=%d requests=%d inserted=%d failed=%d%n",
                date,
                result.status(),
                result.passes(),
                result.attempts(),
                result.inserted(),
                result.failures().size());
        result.failures()
                .forEach(stock -> System.err.printf(
                        "FAILED: %s(%s) %s%n", stock.stockCode(), stock.stockName(), stock.exchanges()));
        assertThat(result.status())
                .isIn(StockDailyPriceCollector.Status.COMPLETE, StockDailyPriceCollector.Status.HOLIDAY);
    }
}
