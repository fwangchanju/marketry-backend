package dev.eolmae.marketry.domain.stock.client;

import static org.assertj.core.api.Assertions.assertThat;

import dev.eolmae.marketry.common.enums.Zone;
import dev.eolmae.marketry.common.exception.BusinessException;
import dev.eolmae.marketry.domain.stock.entity.StockInfo;
import dev.eolmae.marketry.domain.stock.enums.ExchangeType;
import dev.eolmae.marketry.domain.stock.service.StockDailyPriceService;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;

/**
 * Spring 앱의 클라이언트와 서비스를 주입받아 종목·거래소마다 ka11081 첫 페이지(최대600거래일)만 저장한다.
 * <p>로컬 DB는127.0.0.1:15432. 앱과 동일하게 POSTGRES_PASSWORD, KIWOOM_APP_KEY_B, KIWOOM_SECRET_B를 주입한다.
 * 스케줄은 비활성화하며 테스트 전체를 트랜잭션으로 감싸지 않아 페이지별 성공분은 커밋된다.
 * <p>DAILY_PRICE_SYMBOLS=ALL(기본) 또는005930,000660. KRX 전체+현재 nxtEnabled 종목만 NXT도 요청한다.
 * DAILY_PRICE_BASE_DATE=yyyy-MM-dd(기본 오늘). 미확정 오늘 행은 제외하고 과거 NXT는 응답에 있는 날짜만 저장한다.
 * <p>.\gradlew.bat manualTest --tests "*.StockDailyPriceBackfillManualTest" --rerun --info --console=plain
 * 실패 종목·거래소만200ms→500ms→2초 총3회. 기존 행은 유지한다.
 */
@Import(StockDailyPriceBackfillManualTest.BackfillConfiguration.class)
class StockDailyPriceBackfillManualTest extends StockDailyPriceSpringManualTest {
    @Autowired
    private StockDailyPriceBackfillLoader loader;

    @Test
    void 첫페이지의_최대600거래일을_KRX와NXT별로_로컬DB에_저장한다() throws InterruptedException {
        LocalDate today = LocalDate.now(Zone.KST.zoneId());
        LocalDate baseDate = LocalDate.parse(option("DAILY_PRICE_BASE_DATE", today.toString()));
        runBackfill(baseDate, selectedCodes());
    }

    void runBackfill(LocalDate baseDate, List<String> selectedCodes) throws InterruptedException {
        LocalDate today = LocalDate.now(Zone.KST.zoneId());
        assertThat(baseDate).as("기준일은 오늘 이하").isBeforeOrEqualTo(today);
        var targetStocks = stocks.getCache().values().stream()
                .filter(StockInfo::isActiveAndOrdinary)
                .filter(stock -> selectedCodes.isEmpty() || selectedCodes.contains(stock.getStockCode()))
                .sorted(java.util.Comparator.comparing(StockInfo::getStockCode))
                .toList();
        assertThat(targetStocks).as("로컬 stock_info의 활성 일반주 필요").isNotEmpty();
        assertThat(targetStocks.stream().map(StockInfo::getStockCode).toList()).containsAll(selectedCodes);
        List<Target> pending = new ArrayList<>();
        for (StockInfo stock : targetStocks) {
            pending.add(new Target(stock.getStockCode(), ExchangeType.KRX));
            if (stock.isNxtEnabled()) {
                pending.add(new Target(stock.getStockCode(), ExchangeType.NXT));
            }
        }
        int inserted = 0;
        int skipped = 0;
        long lastRequest = 0;
        System.out.printf(
                "START: api=ka11081 baseDate=%s stocks=%d requests=%d firstPageOnly=true%n",
                baseDate, targetStocks.size(), pending.size());
        for (long interval : new long[] {200, 500, 2000}) {
            List<Target> failed = new ArrayList<>();
            for (Target target : pending) {
                long wait = TimeUnit.MILLISECONDS.toNanos(interval) - (System.nanoTime() - lastRequest);
                if (wait > 0) {
                    TimeUnit.NANOSECONDS.sleep(wait);
                }
                lastRequest = System.nanoTime();
                try {
                    var result = loader.load(
                            target.code(), target.exchange(), baseDate, today, LocalDateTime.now(Zone.KST.zoneId()));
                    inserted += result.inserted();
                    skipped += result.skipped();
                    System.out.printf(
                            "PASS: stock=%s exchange=%s rows=%d inserted=%d skipped=%d%n",
                            target.code(),
                            target.exchange(),
                            result.inserted() + result.skipped(),
                            result.inserted(),
                            result.skipped());
                } catch (RuntimeException failure) {
                    failed.add(target);
                    System.err.printf(
                            "FAILED: stock=%s exchange=%s reason=%s%n",
                            target.code(), target.exchange(), failureReason(failure));
                }
            }
            pending = failed;
            System.out.printf("ROUND: intervalMs=%d remaining=%d%n", interval, pending.size());
            if (pending.isEmpty()) {
                break;
            }
        }
        System.out.printf("COMPLETE: inserted=%d skipped=%d failed=%d%n", inserted, skipped, pending.size());
        pending.forEach(target -> System.err.printf("RETRY: stock=%s exchange=%s%n", target.code(), target.exchange()));
        assertThat(pending)
                .as("실패 종목은 DAILY_PRICE_SYMBOLS로 재실행하세요. 성공분은 커밋되어 있습니다.")
                .isEmpty();
    }

    static String failureReason(RuntimeException failure) {
        if (failure instanceof BusinessException business) {
            // 가격 파싱·조회 오류의 고정 문맥만 출력한다. 인증 응답과 원인 예외는 출력하지 않는다.
            return switch (business.getErrorCode()) {
                case KIWOOM_RESPONSE_PARSE_FAILED, KIWOOM_HTTP_ERROR, KIWOOM_ERROR_RESPONSE ->
                    business.createLogMessage();
                default -> "[" + business.getErrorCode().name() + "] " + business.getMessage();
            };
        }
        return failure.getClass().getSimpleName();
    }

    private record Target(String code, ExchangeType exchange) {}

    @TestConfiguration(proxyBeanMethods = false)
    static class BackfillConfiguration {
        @Bean
        StockDailyPriceBackfillLoader stockDailyPriceBackfillLoader(
                StockDailyPriceClient client, StockDailyPriceService prices) {
            return new StockDailyPriceBackfillLoader(client, prices);
        }
    }
}
