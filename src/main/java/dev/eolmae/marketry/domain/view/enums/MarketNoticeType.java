package dev.eolmae.marketry.domain.view.enums;

/** 홈의 안내 틀에 띄우는 특수한 종목의 종류. */
public enum MarketNoticeType {
    /** 거래정지가 연속 5거래일 이어져 지도와 평균에서 빠지기 시작한 종목. */
    TRADING_SUSPENSION,
    /** 오늘 새로 상장한 종목. */
    NEW_LISTING
}
