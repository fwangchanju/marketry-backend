package kr.co.marketry.domain.renderer.client;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.Base64;
import java.util.List;
import java.util.Optional;
import kr.co.marketry.common.exception.ErrorCode;
import kr.co.marketry.common.exception.EscalateException;
import kr.co.marketry.domain.auth.service.AppJwtService;
import kr.co.marketry.domain.notification.properties.MarketryProperties;
import kr.co.marketry.domain.renderer.properties.RendererProperties;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

@Slf4j
@Component
@RequiredArgsConstructor
public class ScreenshotClient {

    private final RendererProperties properties;
    private final MarketryProperties marketryProperties;
    private final AppJwtService appJwtService;

    @Qualifier("rendererRestClient")
    private final RestClient restClient;

    // path: 프론트 base 뒤에 붙일 경로(쿼리 포함 가능), selector: 캡처할 요소들의 CSS 셀렉터
    public List<byte[]> capture(String path, String selector) {
        return captureWithRows(path, selector, null).images();
    }

    // rowSelector: 이미지와 같은 화면에서 data-capture-row 값을 함께 읽을 요소들의 CSS 셀렉터(없으면 null).
    // 이미지에 그려진 숫자를 그대로 캡션에 쓰려는 용도다. 렌더러가 이 기능을 모르는 옛 버전이면 rows는 빈 목록이다.
    public CaptureResult captureWithRows(String path, String selector, String rowSelector) {
        CaptureResponse response = Optional.ofNullable(requestCapture(path, selector, rowSelector))
                .filter(captured -> captured.images() != null)
                .orElseThrow(() -> new EscalateException(ErrorCode.SCREENSHOT_CAPTURE_FAILED));
        List<byte[]> images = response.images().stream()
                .map(img -> Base64.getDecoder().decode(img.data()))
                .toList();
        return new CaptureResult(images, Optional.ofNullable(response.rows()).orElse(List.of()));
    }

    public record CaptureResult(List<byte[]> images, List<String> rows) {}

    private CaptureResponse requestCapture(String path, String selector, String rowSelector) {
        try {
            return restClient
                    .post()
                    .uri(properties.url() + "/capture")
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(new CaptureRequest(path, selector, captureToken(), rowSelector))
                    .retrieve()
                    .body(CaptureResponse.class);
        } catch (Exception e) {
            throw new EscalateException(ErrorCode.SCREENSHOT_CAPTURE_FAILED, e);
        }
    }

    // renderer.owner-capture-enabled가 false면(소유자 데이터 이관 전 기본값) 토큰 없이 예전과 동일하게
    // 동작한다.
    private String captureToken() {
        if (!properties.ownerCaptureEnabled()) {
            return null;
        }
        return appJwtService.issueCaptureToken(marketryProperties.ownerUserId());
    }

    @JsonInclude(JsonInclude.Include.NON_NULL)
    private record CaptureRequest(
            @JsonProperty("path") String path,
            @JsonProperty("selector") String selector,
            @JsonProperty("token") String token,
            @JsonProperty("rowSelector") String rowSelector) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    private record CaptureResponse(List<ImageData> images, List<String> rows) {
        @JsonIgnoreProperties(ignoreUnknown = true)
        private record ImageData(String name, String data) {}
    }
}
