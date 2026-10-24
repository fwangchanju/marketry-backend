package dev.eolmae.marketry.domain.stock.client;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.HashMap;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class KiwoomProxyPreflightTest {

    @ParameterizedTest
    @ValueSource(strings = {"", "   "})
    void 프록시가_비면_직접_연결하지_않고_실패한다(String host) {
        Map<String, String> environment = environment();
        environment.put("MARKETRY_PROXY_HOST", host);

        assertThatThrownBy(() -> KiwoomProxyPreflight.properties(environment))
                .isInstanceOf(AssertionError.class)
                .hasMessageContaining("MARKETRY_PROXY_HOST");
    }

    @Test
    void 프록시_변수가_없어도_직접_연결하지_않는다() {
        Map<String, String> environment = environment();
        environment.remove("MARKETRY_PROXY_HOST");

        assertThatThrownBy(() -> KiwoomProxyPreflight.properties(environment))
                .isInstanceOf(AssertionError.class)
                .hasMessageContaining("MARKETRY_PROXY_HOST");
    }

    @ParameterizedTest
    @ValueSource(strings = {"http://proxy.example.invalid", "proxy.example.invalid:8888", "proxy.example.invalid/path"})
    void 프록시_주소에_URL이나_포트나_경로를_넣으면_실패한다(String host) {
        Map<String, String> environment = environment();
        environment.put("MARKETRY_PROXY_HOST", host);

        assertThatThrownBy(() -> KiwoomProxyPreflight.properties(environment))
                .isInstanceOf(AssertionError.class)
                .hasMessageContaining("MARKETRY_PROXY_HOST");
    }

    @ParameterizedTest
    @ValueSource(strings = {"0", "65536", "-1", "invalid"})
    void 잘못된_포트는_요청_전에_실패한다(String port) {
        Map<String, String> environment = environment();
        environment.put("MARKETRY_PROXY_PORT", port);

        assertThatThrownBy(() -> KiwoomProxyPreflight.properties(environment))
                .isInstanceOf(AssertionError.class)
                .hasMessageContaining("MARKETRY_PROXY_PORT");
    }

    @Test
    void 포트를_생략하면_8888을_쓴다() {
        assertThat(KiwoomProxyPreflight.properties(environment()).proxyPort()).isEqualTo(8888);
    }

    @Test
    void 사용자가_지정한_포트를_쓴다() {
        Map<String, String> environment = environment();
        environment.put("MARKETRY_PROXY_PORT", "9999");

        assertThat(KiwoomProxyPreflight.properties(environment).proxyPort()).isEqualTo(9999);
    }

    @Test
    void 발급을_명시적으로_허용하지_않으면_실패한다() {
        Map<String, String> environment = environment();
        environment.remove("MARKETRY_KIWOOM_TOKEN_TEST");

        assertThatThrownBy(() -> KiwoomProxyPreflight.properties(environment))
                .isInstanceOf(AssertionError.class)
                .hasMessageContaining("MARKETRY_KIWOOM_TOKEN_TEST");
    }

    @ParameterizedTest
    @ValueSource(strings = {"KIWOOM_APP_KEY", "KIWOOM_APP_SECRET"})
    void 인증_값이_비면_요청_전에_실패한다(String name) {
        Map<String, String> environment = environment();
        environment.put(name, " ");

        assertThatThrownBy(() -> KiwoomProxyPreflight.properties(environment))
                .isInstanceOf(AssertionError.class)
                .hasMessageContaining(name);
    }

    @Test
    void 실패_예외와_원인의_비밀값은_보고서에_전달하지_않는다() {
        String sensitive = "test-only-sensitive-response";
        var output = new StringWriter();
        try {
            KiwoomProxyPreflight.request("프록시 인증", () -> {
                throw new IllegalArgumentException(sensitive, new IllegalStateException(sensitive));
            });
            throw new AssertionError("실패 예외가 필요합니다.");
        } catch (AssertionError failure) {
            failure.printStackTrace(new PrintWriter(output));
            assertThat(failure.getCause()).isNull();
            assertThat(failure.getMessage()).contains("프록시 인증 실패");
            assertThat(output.toString()).doesNotContain(sensitive);
        }
    }

    private Map<String, String> environment() {
        return new HashMap<>(Map.of(
                "MARKETRY_KIWOOM_TOKEN_TEST", "1",
                "MARKETRY_PROXY_HOST", "proxy.example.invalid",
                "KIWOOM_APP_KEY", "test-key",
                "KIWOOM_APP_SECRET", "test-secret"));
    }
}
