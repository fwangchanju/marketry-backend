package dev.eolmae.marketry.domain.notification.enums;

// 렌더러가 연 화면의 등락률 기준 — 프론트가 ?basis= 값으로 읽는다(값은 프론트와 합의). 기준을 안 주면 누적이다.
public enum RenderBasis {
    // 정규장 종가 기준 화면(정규장 마감~애프터 마켓 시작 사이)
    CLOSE("close"),
    // 애프터 마켓 별도(정규장 종가 대비) 화면
    AFTER_HOURS("afterHours");

    private final String queryValue;

    RenderBasis(String queryValue) {
        this.queryValue = queryValue;
    }

    public String queryValue() {
        return queryValue;
    }
}
