package kr.co.marketry.domain.notification.client;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.ExpectedCount.once;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.anything;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withException;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.nio.channels.ClosedChannelException;
import java.util.List;
import kr.co.marketry.domain.notification.exception.TelegramSendException;
import kr.co.marketry.domain.notification.properties.TelegramProperties;
import kr.co.marketry.domain.view.enums.AverageMode;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

class TelegramClientTest {

    private static final String BOT_TOKEN = "123456:SECRET-TOKEN";

    private final TelegramProperties properties =
            new TelegramProperties(BOT_TOKEN, "chat", "dev-chat", 10, 15, 15, AverageMode.SIMPLE, true);

    private TelegramClient clientFailingWith(IOException failure) {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        server.expect(once(), anything()).andRespond(withException(failure));
        return new TelegramClient(properties, builder.build(), new ObjectMapper());
    }

    @Test
    void sendPhoto_연결이_취소되면_진단_정보를_예외_메시지에_담는다() {
        TelegramClient client = clientFailingWith(new IOException("Request cancelled", new ClosedChannelException()));

        assertThatThrownBy(() -> client.sendPhoto("chat", new byte[2048], "caption"))
                .isInstanceOf(TelegramSendException.class)
                .hasMessageContaining("ResourceAccessException")
                .hasMessageContaining("Request cancelled")
                .hasMessageContaining("진단: 소요 ")
                .hasMessageContaining("전송 2048B")
                .hasMessageContaining("원인 체인 IOException(Request cancelled) > ClosedChannelException(null)")
                .hasMessageContaining("중단 표시 false");
    }

    @Test
    void sendPhoto_원인_체인에_토큰이_섞여_있어도_마스킹한다() {
        TelegramClient client =
                clientFailingWith(new IOException("https://api.telegram.org/bot" + BOT_TOKEN + "/sendPhoto 연결 끊김"));

        assertThatThrownBy(() -> client.sendPhoto("chat", new byte[10], null))
                .isInstanceOf(TelegramSendException.class)
                .message()
                .doesNotContain(BOT_TOKEN)
                .contains("bot***");
    }

    @Test
    void sendMessage_전송_크기는_UTF8_바이트_수로_센다() {
        TelegramClient client = clientFailingWith(new IOException("끊김"));

        assertThatThrownBy(() -> client.sendMessage("chat", "가나다"))
                .isInstanceOf(TelegramSendException.class)
                .hasMessageContaining("전송 9B");
    }

    @Test
    void sendMediaGroup_전송_크기는_사진_바이트_수의_합이다() {
        TelegramClient client = clientFailingWith(new IOException("끊김"));

        assertThatThrownBy(() -> client.sendMediaGroup("chat", List.of(new byte[100], new byte[250]), "caption"))
                .isInstanceOf(TelegramSendException.class)
                .hasMessageContaining("전송 350B");
    }

    @Test
    void sendPhoto_원인이_없는_실패는_원인_체인을_없음으로_적는다() {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        server.expect(once(), anything()).andRespond(request -> {
            throw new IllegalStateException("직접 던진 실패");
        });
        TelegramClient client = new TelegramClient(properties, builder.build(), new ObjectMapper());

        assertThatThrownBy(() -> client.sendPhoto("chat", new byte[1], null))
                .isInstanceOf(TelegramSendException.class)
                .hasMessageContaining("원인 체인 없음");
    }
}
