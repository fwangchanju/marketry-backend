package kr.co.marketry.domain.view.enums;

import kr.co.marketry.common.exception.BadRequestException;
import kr.co.marketry.common.exception.ErrorCode;

/**
 * 지도가 어떤 분류 데이터로 그려지는지.
 *
 * <ul>
 *   <li>{@code KRX} — 한국거래소 분류. 누구나
 *   <li>{@code MARKETRY} — 운영자가 올려 둔 고정본. 로그인 없이 누구나 읽기만 한다
 *   <li>{@code MINE} — 로그인한 본인의 분류(내 분류)
 * </ul>
 */
public enum ClassificationSource {
    KRX,
    MARKETRY,
    MINE;

    /** 쿼리 파라미터({@code krx} | {@code marketry} | {@code mine}, 대소문자 무시) 해석 — 모르는 값은 거부한다.
     * 이름을 바꾸기 전 화면이 보내던 {@code mymap}도 배포 전환 동안 {@code mine}으로 받는다. */
    public static ClassificationSource from(String source) {
        if ("mymap".equalsIgnoreCase(source)) {
            return MINE;
        }
        for (ClassificationSource candidate : values()) {
            if (candidate.name().equalsIgnoreCase(source)) {
                return candidate;
            }
        }
        throw new BadRequestException(ErrorCode.CLASSIFICATION_SOURCE_INVALID, source);
    }

    /** {@code source}가 없으면 옛 {@code isCustom}대로(true=내 분류, false=거래소), 있으면 {@code isCustom}은 무시한다. */
    public static ClassificationSource resolve(String source, boolean isCustom) {
        if (source == null) {
            if (isCustom) {
                return MINE;
            }
            return KRX;
        }
        return from(source);
    }
}
