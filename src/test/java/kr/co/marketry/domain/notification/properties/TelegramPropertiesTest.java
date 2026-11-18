package kr.co.marketry.domain.notification.properties;

import static org.assertj.core.api.Assertions.assertThat;

import kr.co.marketry.domain.view.enums.AverageMode;
import org.junit.jupiter.api.Test;

class TelegramPropertiesTest {

    private TelegramProperties withDeveloperChatId(String developerChatId) {
        return new TelegramProperties("token", "chat", developerChatId, 10, 15, 15, AverageMode.SIMPLE, true);
    }

    @Test
    void developerChatIds_한_곳만_적으면_그_한_곳이다() {
        assertThat(withDeveloperChatId("111").developerChatIds()).containsExactly("111");
    }

    @Test
    void developerChatIds_쉼표로_구분한_여러_곳을_순서대로_푼다() {
        assertThat(withDeveloperChatId("111,222,333").developerChatIds()).containsExactly("111", "222", "333");
    }

    @Test
    void developerChatIds_앞뒤_공백과_빈_칸은_버린다() {
        assertThat(withDeveloperChatId(" 111 , ,222,, ").developerChatIds()).containsExactly("111", "222");
    }

    @Test
    void developerChatIds_값이_없으면_빈_목록이다() {
        assertThat(withDeveloperChatId(null).developerChatIds()).isEmpty();
        assertThat(withDeveloperChatId("").developerChatIds()).isEmpty();
        assertThat(withDeveloperChatId("  ,  ").developerChatIds()).isEmpty();
    }
}
