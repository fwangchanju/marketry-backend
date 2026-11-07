package kr.co.marketry.config;

import static org.assertj.core.api.Assertions.assertThat;

import java.lang.reflect.Constructor;
import java.lang.reflect.Parameter;
import java.util.Arrays;
import kr.co.marketry.domain.notification.client.TelegramClient;
import kr.co.marketry.domain.renderer.client.ScreenshotClient;
import kr.co.marketry.domain.stock.client.KiwoomApiClient;
import kr.co.marketry.domain.stock.client.KiwoomTokenManager;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.web.client.RestClient;

/**
 * lombok.config(lombok.copyableAnnotations += ...Qualifier)가 @RequiredArgsConstructor 생성자에
 * @Qualifier를 실제로 복사하는지 리플렉션으로 고정한다 — 이게 안 되면 네 클라이언트가 전부 @Primary인
 * 기본 restClient(read 10초)를 조용히 주입받아, ApplicationConfig에서 클라이언트별로 나눠둔
 * read 타임아웃(telegram 30초/renderer 90초/kiwoom 10초)이 전부 무효가 된다.
 *
 * 한계: 이 테스트는 lombok.config 파일이 레포에서 아예 사라지는 경우는 잡지만, Dockerfile이 그
 * 파일을 빌드 컨텍스트에 복사하지 않아서 컨테이너 안에서만 사라지는 경우(이번 핫픽스의 실제 원인)는
 * 잡지 못한다 — 로컬/CI 둘 다 레포 루트에서 gradle을 직접 돌리기 때문이다. 그건 실제 Dockerfile로
 * 이미지를 빌드해서 javap로 확인하는 수밖에 없다.
 */
class RestClientQualifierTest {

    @Test
    void telegramClient_RestClient_파라미터에_telegramRestClient_Qualifier가_붙어있다() {
        assertQualifier(TelegramClient.class, "telegramRestClient");
    }

    @Test
    void screenshotClient_RestClient_파라미터에_rendererRestClient_Qualifier가_붙어있다() {
        assertQualifier(ScreenshotClient.class, "rendererRestClient");
    }

    @Test
    void kiwoomApiClient_RestClient_파라미터에_kiwoomRestClient_Qualifier가_붙어있다() {
        assertQualifier(KiwoomApiClient.class, "kiwoomRestClient");
    }

    @Test
    void kiwoomTokenManager_RestClient_파라미터에_kiwoomRestClient_Qualifier가_붙어있다() {
        assertQualifier(KiwoomTokenManager.class, "kiwoomRestClient");
    }

    private void assertQualifier(Class<?> targetClass, String expectedQualifier) {
        Parameter parameter = restClientParameter(targetClass);
        Qualifier qualifier = parameter.getAnnotation(Qualifier.class);

        assertThat(qualifier)
                .as("%s의 RestClient 생성자 파라미터", targetClass.getSimpleName())
                .isNotNull();
        assertThat(qualifier.value()).isEqualTo(expectedQualifier);
    }

    // 파라미터 인덱스를 하드코딩하지 않고 타입(RestClient)으로 찾는다 — 생성자 파라미터 순서가
    // 바뀌어도 이 테스트가 깨지지 않게.
    private Parameter restClientParameter(Class<?> targetClass) {
        Constructor<?> constructor = targetClass.getDeclaredConstructors()[0];
        return Arrays.stream(constructor.getParameters())
                .filter(parameter -> parameter.getType() == RestClient.class)
                .findFirst()
                .orElseThrow(() -> new IllegalStateException(targetClass.getSimpleName() + "에 RestClient 파라미터가 없다"));
    }
}
