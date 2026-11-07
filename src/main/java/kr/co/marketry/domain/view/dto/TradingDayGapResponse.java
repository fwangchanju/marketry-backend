package kr.co.marketry.domain.view.dto;

/** 두 날짜 사이의 거래일 수 — 달력에서 고른 날이 실시간 날짜보다 몇 거래일 전인지 보여 줄 때 쓴다. */
public record TradingDayGapResponse(long tradingDays) {}
