package kr.co.marketry.domain.view.dto;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * 종목 차트의 일봉 한 칸. 원주가(KRW)라 분할·병합 전후의 가격은 이어 붙이지 않는다.
 *
 * <p>provisional이 true면 아직 일봉이 저장되지 않은 날(장중)의 임시 봉이다. 5분 시세의 첫 값·최고·최저·마지막 값으로 만들어 실제
 * 시가·고가·저가와 다를 수 있고, 거래량은 없어서 0이다.
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
