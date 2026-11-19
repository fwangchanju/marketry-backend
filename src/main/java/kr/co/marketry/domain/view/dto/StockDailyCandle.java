package kr.co.marketry.domain.view.dto;

import java.math.BigDecimal;
import java.time.LocalDate;

/** 종목 차트의 일봉 한 칸. 원주가(KRW)라 분할·병합 전후의 가격은 이어 붙이지 않는다. */
public record StockDailyCandle(
        LocalDate date, BigDecimal open, BigDecimal high, BigDecimal low, BigDecimal close, long volume) {}
