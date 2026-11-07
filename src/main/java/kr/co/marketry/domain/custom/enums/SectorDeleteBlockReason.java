package kr.co.marketry.domain.custom.enums;

/** 업종을 삭제할 수 없는 이유. */
public enum SectorDeleteBlockReason {
    PROTECTED, // 신규 상장·거래 정지·미분류
    HAS_SUB_SECTORS // 하위 업종이 있다
}
