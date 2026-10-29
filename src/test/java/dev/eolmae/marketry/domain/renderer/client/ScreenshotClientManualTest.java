package dev.eolmae.marketry.domain.renderer.client;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import dev.eolmae.marketry.domain.auth.properties.AuthProperties;
import dev.eolmae.marketry.domain.auth.service.AppJwtService;
import dev.eolmae.marketry.domain.notification.enums.RenderTarget;
import dev.eolmae.marketry.domain.notification.properties.MarketryProperties;
import dev.eolmae.marketry.domain.renderer.properties.RendererProperties;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.web.client.RestClient;

/**
 * 렌더러 컨테이너가 실제로 스크린샷을 캡처하는지 확인하는 수동 검증용 테스트.
 * Spring 컨텍스트 없이 ScreenshotClient만 직접 구성해서 실제 RENDERER_URL에 네트워크로 접근하므로,
 * 배포 후 렌더러가 정상 동작하는지 확인할 때만 실행한다.
 *
 * 실행 조건: RENDERER_URL 환경변수(없으면 기본값 http://market-monitor-renderer:3000 사용) —
 * 렌더러 컨테이너가 그 주소로 실제 접근 가능해야 한다.
 * 실행 명령: ./gradlew manualTest --tests "*.ScreenshotClientManualTest" -i
 */
@Tag("manual")
class ScreenshotClientManualTest {

    @Test
    void capturesNonEmptyImages() {
        String rendererUrl = System.getenv().getOrDefault("RENDERER_URL", "http://market-monitor-renderer:3000");
        var client = new ScreenshotClient(
                new RendererProperties(rendererUrl, false),
                new MarketryProperties(rendererUrl, 999999L, 900000L),
                new AppJwtService(new AuthProperties(), new ObjectMapper()),
                RestClient.create());

        assertThat(client.capture(RenderTarget.HOME_MOBILE.path(), RenderTarget.HOME_MOBILE.selector()))
                .isNotEmpty();
    }
}
