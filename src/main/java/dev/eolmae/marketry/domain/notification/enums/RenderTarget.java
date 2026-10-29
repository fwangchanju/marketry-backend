package dev.eolmae.marketry.domain.notification.enums;

import dev.eolmae.marketry.common.enums.Market;
import dev.eolmae.marketry.domain.view.enums.MarketQuery;
import java.util.Locale;
import java.util.Optional;

// 캡처할 프론트 페이지와 그 안에서 스크린샷 찍을 영역(data-captureid, 프론트와 값 합의)
public enum RenderTarget {
    // 홈 화면을 지도 페이지 모바일 캡처와 같은 3:4 세로 판(툴바·시장 지도·그룹 등락률)으로 찍는다 — 프론트가 capture=mobile일 때만 이 판을 그린다.
    HOME_MOBILE("/home?capture=mobile", "summary-capture"),
    // 지도 페이지 모바일 캡처(스크린샷 창의 "모바일" 3:4 판)를 화면에 그대로 펼친 것 — 프론트가 capture=mobile일 때만 이 판을 그린다.
    MAP_MOBILE("/map/korea?capture=mobile", "map-mobile-capture");

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

    // 캡처 경로의 마켓 세그먼트(kospi/kosdaq/korea). 마켓 하나(Market)와 조회 단위(MarketQuery,
    // ALL_STOCK 포함) 둘 다 캡처 대상이 되므로 오버로드로 받는다 — 아무 문자열이나 넘기지 못하게.
    public static String marketSegment(Market market) {
        return toSegment(market.name());
    }

    public static String marketSegment(MarketQuery query) {
        return toSegment(query.name());
    }

    // 프론트 라우트는 /map/kospi, /map/kosdaq, /map/korea다. 전체 종목(ALL_STOCK)은 korea이고,
    // 옛 이름 allstock은 프론트에서 없어졌다(2026-10-28 옛 주소 정리) — 그 주소를 열면 /home으로 튕겨 캡처 요소가 없다.
    private static String toSegment(String enumName) {
        return "ALL_STOCK".equals(enumName) ? "korea" : enumName.toLowerCase(Locale.ROOT);
    }
}
