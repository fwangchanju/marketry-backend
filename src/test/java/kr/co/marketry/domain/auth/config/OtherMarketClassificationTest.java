package kr.co.marketry.domain.auth.config;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;

/** 일본·미국 내 기준은 공개하기 전까지 관리자만 쓴다. 관리자 규칙에 걸리는 요청을 고정한다. */
class OtherMarketClassificationTest {

    @Test
    void 일본_미국_내_기준_요청은_관리자_규칙에_걸린다() {
        for (String path : new String[] {
            "/api/custom/sectors",
            "/api/custom/sectors/3/name",
            "/api/custom/stock-sectors/assignments",
            "/api/custom/stock-sectors/upload",
            "/api/custom/snapshots/1/restore",
            "/api/map/excluded-sectors/3",
            "/api/%63ustom/sectors"
        }) {
            assertThat(isOther(path, "JAPAN")).as(path).isTrue();
            assertThat(isOther(path, "USA")).as(path).isTrue();
            assertThat(isOther(path, "KOREA", "JAPAN")).as(path).isTrue();
            assertThat(isOther(path, "japan")).as(path).isTrue();
        }
    }

    @Test
    void 한국이거나_시장을_적지_않으면_지금처럼_회원이_쓴다() {
        assertThat(isOther("/api/custom/sectors")).isFalse();
        assertThat(isOther("/api/custom/sectors", "KOREA")).isFalse();
        // 시장 이름이 다른 뜻인 주소(지도 조회)는 막지 않는다.
        assertThat(isOther("/api/map", "ALL_STOCK")).isFalse();
    }

    private static boolean isOther(String path, String... markets) {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", path);
        if (markets.length > 0) {
            request.addParameter("market", markets);
        }
        return AuthConfiguration.isOtherMarketClassification(request);
    }
}
