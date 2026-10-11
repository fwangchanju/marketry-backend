package dev.eolmae.marketry.domain.view.service;

import dev.eolmae.marketry.domain.stock.service.ClosingPrices;
import dev.eolmae.marketry.domain.stock.service.SectorPriceCacheService.CachedStockPrice;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.HashMap;
import java.util.Map;

/** 시간외 등락률(그날 정규장 종가 대비) 계산 — 시각·DB와 무관한 순수 함수만 모았다. */
final class AfterHoursChangeRates {

    // DB(change_rate)와 같은 소수 자릿수.
    private static final int RATE_SCALE = 4;
    private static final BigDecimal HUNDRED = BigDecimal.valueOf(100);

    private AfterHoursChangeRates() {}

    /**
     * 시간외 기준을 적용할 수 있는 스냅샷인지. 그 스냅샷 날짜의 {@code afterHoursStart}(15:40) 이후일 때만이다 — 장중에는
     * 시간외 등락률이 존재하지 않는다(그 구간은 키움 값을 쓴다). 날짜는 따지지 않는다: 장이 끝난 뒤 다음 개장까지는
     * 마지막 스냅샷(전 거래일 15:40 이후)이 계속 보이고, 그 스냅샷 날짜의 종가로 계산한다.
     */
    static boolean isApplicable(LocalDateTime snapshotTime, LocalTime afterHoursStart) {
        return !snapshotTime.toLocalTime().isBefore(afterHoursStart);
    }

    /**
     * 종목별 등락률을 {@code (현재가 − 종가) ÷ 종가 × 100}으로 바꾼다. 종가가 없는 종목(15:35에는 없다가 이후에
     * 나타난 종목 등)은 결과에서 뺀다 — 0으로 채우면 "안 움직였다"와 "값이 없다"가 구분되지 않는다. 종가가 0이어도
     * 뺀다(나눌 수 없다).
     */
    static Map<String, CachedStockPrice> apply(Map<String, CachedStockPrice> priceByStockCode, ClosingPrices closing) {
        Map<String, CachedStockPrice> result = new HashMap<>();
        priceByStockCode.forEach((stockCode, price) -> {
            BigDecimal base = closing.priceByStockCode().get(stockCode);
            if (base == null || base.signum() == 0) {
                return;
            }
            BigDecimal rate = price.currentPrice()
                    .subtract(base)
                    .multiply(HUNDRED)
                    .divide(base, RATE_SCALE, RoundingMode.HALF_UP);
            result.put(
                    stockCode,
                    new CachedStockPrice(price.currentPrice(), rate, price.snapshotTime(), price.lastPrice()));
        });
        return result;
    }
}
