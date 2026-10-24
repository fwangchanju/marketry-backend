package dev.eolmae.marketry.domain.notification.enums;

import dev.eolmae.marketry.common.enums.Market;
import dev.eolmae.marketry.domain.view.enums.MarketQuery;
import java.util.Locale;
import java.util.Optional;

// 캡처할 프론트 페이지와 그 안에서 스크린샷 찍을 영역(data-captureid, 프론트와 값 합의)
public enum RenderTarget {
    SUMMARY("/summary", "summary-capture"),
    // 홈 화면을 지도 페이지 모바일 캡처와 같은 3:4 세로 판(툴바·시장 지도·그룹 등락률)으로 찍는다 — 프론트가 capture=mobile일 때만 이 판을 그린다.
    HOME_MOBILE("/home?capture=mobile", "summary-capture"),
    // 지도 페이지 모바일 캡처(스크린샷 창의 "모바일" 3:4 판)를 화면에 그대로 펼친 것 — 프론트가 capture=mobile일 때만 이 판을 그린다.
    MAP_MOBILE("/map/allstock?capture=mobile", "map-mobile-capture"),
    MAP("/map", "map-capture"),
    SECTOR("/sector", "sector-capture");

    private static final String CAPTURE_ATTRIBUTE = "data-captureid";

    private final String path;
    private final String captureId;

    RenderTarget(String path, String captureId) {
        this.path = path;
        this.captureId = captureId;
    }

    public String path() {
        return path;
    }

    // 등락률 기준을 주소에 붙인다. 기준 지정은 프론트의 capture=mobile 판만 읽으므로 그 두 대상에만 쓴다.
    public String path(Optional<RenderBasis> basis) {
        return basis.map(value -> path + "&basis=" + value.queryValue()).orElse(path);
    }

    public String selector() {
        return "[" + CAPTURE_ATTRIBUTE + "='" + captureId + "']";
    }

    // 캡처 경로의 마켓 세그먼트(kospi/kosdaq/allstock). 마켓 하나(Market)와 조회 단위(MarketQuery,
    // ALL_STOCK 포함) 둘 다 캡처 대상이 되므로 오버로드로 받는다 — 아무 문자열이나 넘기지 못하게.
    public static String marketSegment(Market market) {
        return toSegment(market.name());
    }

    public static String marketSegment(MarketQuery query) {
        return toSegment(query.name());
    }

    // MarketQuery.ALL_STOCK처럼 상수명에 "_"가 있어도 프론트 라우트(/map/allstock)엔 없으므로 제거한다.
    private static String toSegment(String enumName) {
        return enumName.toLowerCase(Locale.ROOT).replace("_", "");
    }
}
