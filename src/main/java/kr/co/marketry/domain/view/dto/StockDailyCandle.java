package kr.co.marketry.domain.view.dto;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * 종목 차트의 일봉 한 칸. 원주가(KRW)라 분할·병합 전후의 가격은 이어 붙이지 않는다.
 *
 * <p>provisional이 true면 아직 확정되지 않은 날(장중)의 임시 봉이다. 한국은 5분 시세의 첫 값·최고·최저·마지막 값으로 만들어 실제
 * 시가·고가·저가와 다를 수 있고 거래량은 0이다. 일본은 장중에 받은 야후 일봉(실제 시가·고가·저가·그때까지 거래량)이다.
 */
public record StockDailyCandle(
        LocalDate date,
        BigDecimal open,
        BigDecimal high,
        BigDecimal low,
        BigDecimal close,
        long volume,
        boolean provisional) {

    public StockDailyCandle(
            LocalDate date, BigDecimal open, BigDecimal high, BigDecimal low, BigDecimal close, long volume) {
        this(date, open, high, low, close, volume, false);
    }
}
