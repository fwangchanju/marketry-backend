package kr.co.marketry.domain.view.enums;

/**
 * 등락률을 무엇 대비로 보여줄지.
 *
 * <ul>
 *   <li>{@code DAILY} — 키움이 주는 전일 종가 대비 누적 등락률(기본)
 *   <li>{@code AFTER_HOURS} — 그날 정규장 종가(15:30~15:40 구간의 마지막 스냅샷) 대비 등락률. 장 마감 후 얼마나
 *       움직였는지를 본다
 * </ul>
 */
public enum ChangeRateMode {
    DAILY,
    AFTER_HOURS;

    /** 쿼리 파라미터({@code daily} | {@code afterHours}) 해석 — 모르는 값은 기본(DAILY)으로 본다. */
    public static ChangeRateMode parse(String value) {
        return "afterHours".equalsIgnoreCase(value) ? AFTER_HOURS : DAILY;
    }
}
