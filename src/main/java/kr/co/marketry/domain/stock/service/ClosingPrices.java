package kr.co.marketry.domain.stock.service;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Map;

/**
 * 그날 종가 기준가 — 종목코드 → 가격.
 *
 * <p>{@code baseDate}를 값에 같이 담는 것이 핵심이다. 안 그러면 20:00에 적재된 캐시가 다음 날 수집 때까지
 * 살아남아 어제 종가로 오늘 등락률을 계산한다. 읽는 쪽이 {@code baseDate}가 오늘이 아니면 버리고 다시
 * 적재한다.
 *
 * <p>종가 윈도우에 스냅샷이 하나도 없는 날은 {@code priceByStockCode}가 비어 있다. 그날은 시간외 값이 없는
 * 날이며, 0으로 채우지 않는다.
 */
public record ClosingPrices(LocalDate baseDate, Map<String, BigDecimal> priceByStockCode) {

    public boolean isFor(LocalDate date) {
        return baseDate.equals(date);
    }
}
