package kr.co.marketry.domain.stock.client;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class KiwoomProxyCheckTest {

    @TempDir
    Path directory;

    @Test
    void 검사용_네_변수만_읽고_다른_운영값과_발급_허용은_읽지_않는다() throws IOException {
        Path fixture = directory.resolve("connection-fixture.txt");
        Files.writeString(fixture, """
                # 실제 환경 파일이 아닌 검사 전용 가짜 값
                KIWOOM_APP_KEY='test-key'
                KIWOOM_APP_SECRET="test-secret"
                MARKETRY_PROXY_HOST=proxy.example.invalid
                MARKETRY_PROXY_PORT=8888
                TELEGRAM_BOT_TOKEN=unused-test-value
                MARKETRY_KIWOOM_TOKEN_TEST=1
                """);

        var values = KiwoomProxyCheck.connectionValues(fixture);

        assertThat(values)
                .hasSize(4)
                .containsEntry("KIWOOM_APP_KEY", "test-key")
                .containsEntry("KIWOOM_APP_SECRET", "test-secret")
                .containsEntry("MARKETRY_PROXY_HOST", "proxy.example.invalid")
                .containsEntry("MARKETRY_PROXY_PORT", "8888")
                .doesNotContainKeys("TELEGRAM_BOT_TOKEN", "MARKETRY_KIWOOM_TOKEN_TEST");
    }

    @Test
    void 값에_등호가_포함돼도_보존하고_파일을_셸로_실행하지_않는다() throws IOException {
        Path fixture = directory.resolve("literal-fixture.txt");
        String literal = "test=literal$(not-a-command)";
        Files.writeString(fixture, "KIWOOM_APP_SECRET='" + literal + "'\n");

        assertThat(KiwoomProxyCheck.connectionValues(fixture)).containsEntry("KIWOOM_APP_SECRET", literal);
    }

    @Test
    void 파일을_읽지_못해도_파일_경로와_원래_예외를_출력하지_않는다() {
        Path missing = directory.resolve("test-only-private-name.txt");

        assertThatThrownBy(() -> KiwoomProxyCheck.connectionValues(missing))
                .isInstanceOf(AssertionError.class)
                .hasMessageContaining("계정과 파일 권한")
                .hasMessageNotContaining("test-only-private-name")
                .hasCause(null);
    }
}
