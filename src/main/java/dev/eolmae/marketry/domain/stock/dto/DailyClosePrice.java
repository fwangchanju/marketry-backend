package dev.eolmae.marketry.domain.stock.dto;

import java.math.BigDecimal;

/** 일봉 조회에서 종가만 꺼낼 때 쓰는 열 선택용 투영이다. */
public record DailyClosePrice(String stockCode, BigDecimal closePrice) {}
