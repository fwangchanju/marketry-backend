package dev.eolmae.marketry.domain.view.service;

import static org.assertj.core.api.Assertions.assertThat;

import dev.eolmae.marketry.domain.stock.service.ClosingPrices;
import dev.eolmae.marketry.domain.stock.service.SectorPriceCacheService.CachedStockPrice;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.Map;
import org.junit.jupiter.api.Test;

class AfterHoursChangeRatesTest {

    private static final LocalDate TODAY = LocalDate.of(2026, 10, 2);
    private static final LocalTime AFTER_HOURS_START = LocalTime.of(15, 40);
    private static final LocalDateTime SNAPSHOT_TIME = TODAY.atTime(17, 0);

    private static CachedStockPrice price(String current, String kiwoomRate) {
        return new CachedStockPrice(new BigDecimal(current), new BigDecimal(kiwoomRate), SNAPSHOT_TIME);
    }

    private static ClosingPrices closing(Map<String, BigDecimal> prices) {
        return new ClosingPrices(TODAY, prices);
    }

    @Test
    void 등락률은_종가_대비로_계산하고_소수_네_자리로_반올림한다() {
        Map<String, CachedStockPrice> prices = Map.of(
                "UP", price("101", "5.0"),
                "DOWN", price("99", "5.0"),
                "ROUND", price("286500", "5.0"));
        ClosingPrices closing = closing(
                Map.of("UP", new BigDecimal("100"), "DOWN", new BigDecimal("100"), "ROUND", new BigDecimal("280000")));

        Map<String, CachedStockPrice> result = AfterHoursChangeRates.apply(prices, closing);

        assertThat(result.get("UP").changeRate()).isEqualByComparingTo("1.0000");
        assertThat(result.get("DOWN").changeRate()).isEqualByComparingTo("-1.0000");
        assertThat(result.get("ROUND").changeRate()).isEqualByComparingTo("2.3214");
    }

    @Test
    void 키움_등락률은_결과에_쓰이지_않고_현재가와_시각은_그대로다() {
        Map<String, CachedStockPrice> prices = Map.of("A", price("100", "29.9"));

        Map<String, CachedStockPrice> result =
                AfterHoursChangeRates.apply(prices, closing(Map.of("A", new BigDecimal("100"))));

        assertThat(result.get("A").changeRate()).isEqualByComparingTo("0");
        assertThat(result.get("A").currentPrice()).isEqualByComparingTo("100");
        assertThat(result.get("A").snapshotTime()).isEqualTo(SNAPSHOT_TIME);
    }

    @Test
    void 결과의_전일_종가_자리에는_기준이_된_종가가_들어간다() {
        Map<String, CachedStockPrice> prices = Map.of("A", price("101", "1.0").withLastPrice(new BigDecimal("90")));

        Map<String, CachedStockPrice> result =
                AfterHoursChangeRates.apply(prices, closing(Map.of("A", new BigDecimal("100"))));

        assertThat(result.get("A").lastPrice()).isEqualByComparingTo("100");
    }

    @Test
    void 종가가_없거나_0인_종목은_결과에서_빠진다() {
        Map<String, CachedStockPrice> prices =
                Map.of("NO_BASE", price("100", "1.0"), "ZERO_BASE", price("100", "1.0"), "OK", price("100", "1.0"));
        ClosingPrices closing = closing(Map.of("ZERO_BASE", BigDecimal.ZERO, "OK", new BigDecimal("100")));

        Map<String, CachedStockPrice> result = AfterHoursChangeRates.apply(prices, closing);

        assertThat(result).containsOnlyKeys("OK");
    }

    @Test
    void 그날의_15시40분_이후_스냅샷에는_적용할_수_있다() {
        assertThat(AfterHoursChangeRates.isApplicable(TODAY.atTime(15, 40), AFTER_HOURS_START))
                .isTrue();
        assertThat(AfterHoursChangeRates.isApplicable(TODAY.atTime(20, 0), AFTER_HOURS_START))
                .isTrue();
    }

    @Test
    void 장중_스냅샷에는_적용할_수_없다() {
        assertThat(AfterHoursChangeRates.isApplicable(TODAY.atTime(15, 39), AFTER_HOURS_START))
                .isFalse();
        assertThat(AfterHoursChangeRates.isApplicable(TODAY.atTime(9, 0), AFTER_HOURS_START))
                .isFalse();
    }

    @Test
    void 지난_날짜의_15시40분_이후_스냅샷에도_적용할_수_있다() {
        // 장이 끝난 뒤 다음 개장까지는 전 거래일 마지막 스냅샷이 계속 보인다.
        assertThat(AfterHoursChangeRates.isApplicable(TODAY.minusDays(1).atTime(17, 0), AFTER_HOURS_START))
                .isTrue();
    }

    @Test
    void 지난_날짜의_장중_스냅샷에는_적용할_수_없다() {
        assertThat(AfterHoursChangeRates.isApplicable(TODAY.minusDays(1).atTime(10, 0), AFTER_HOURS_START))
                .isFalse();
    }
}
