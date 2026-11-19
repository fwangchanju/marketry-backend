package kr.co.marketry.runner;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import kr.co.marketry.domain.notification.properties.TelegramProperties;
import kr.co.marketry.domain.view.enums.AverageMode;
import org.junit.jupiter.api.Test;
import org.springframework.boot.DefaultApplicationArguments;

class TelegramConfigurationCheckRunnerTest {

    private TelegramProperties properties(String botToken, String chatId) {
        return new TelegramProperties(botToken, chatId, "dev", 5, 5, 15, AverageMode.SIMPLE, true);
    }

    private TelegramConfigurationCheckRunner runnerOf(String botToken, String chatId) {
        return new TelegramConfigurationCheckRunner(properties(botToken, chatId));
    }

    @Test
    void 토큰과_채팅방이_모두_있으면_통과한다() {
        assertThatCode(() -> runnerOf("token", "chat").run(new DefaultApplicationArguments()))
                .doesNotThrowAnyException();
    }

    @Test
    void 토큰이_비어_있으면_시작하지_않는다() {
        assertThatThrownBy(() -> runnerOf("", "chat").run(new DefaultApplicationArguments()))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("TELEGRAM_BOT_TOKEN")
                .hasMessageNotContaining("TELEGRAM_CHAT_ID");
    }

    @Test
    void 채팅방이_비어_있으면_시작하지_않는다() {
        assertThatThrownBy(() -> runnerOf("token", "  ").run(new DefaultApplicationArguments()))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("TELEGRAM_CHAT_ID");
    }

    @Test
    void 값이_null이어도_비어_있는_것으로_본다() {
        assertThat(TelegramConfigurationCheckRunner.findMissingEnvironmentVariables(properties(null, null)))
                .containsExactly("TELEGRAM_BOT_TOKEN", "TELEGRAM_CHAT_ID");
    }

    @Test
    void 예외_메시지에_값은_싣지_않는다() {
        assertThatThrownBy(() -> runnerOf("secret-token", "").run(new DefaultApplicationArguments()))
                .hasMessageNotContaining("secret-token");
    }
}
