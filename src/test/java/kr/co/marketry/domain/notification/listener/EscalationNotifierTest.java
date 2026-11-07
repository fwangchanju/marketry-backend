package kr.co.marketry.domain.notification.listener;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;

import kr.co.marketry.common.event.EscalationEvent;
import kr.co.marketry.domain.notification.client.TelegramClient;
import kr.co.marketry.domain.notification.exception.TelegramSendException;
import kr.co.marketry.domain.notification.properties.TelegramProperties;
import kr.co.marketry.domain.view.enums.AverageMode;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

class EscalationNotifierTest {

    private final TelegramClient telegramClient = Mockito.mock(TelegramClient.class);
    private final TelegramProperties properties =
            new TelegramProperties("token", "chat", "dev-chat", 10, 15, 15, AverageMode.SIMPLE, true);
    private final EscalationNotifier notifier = new EscalationNotifier(telegramClient, properties);

    @Test
    void onEscalation_발송이_실패해도_예외를_전파하지_않는다() {
        doThrow(new TelegramSendException("RestClientException", "발송 실패"))
                .when(telegramClient)
                .sendMessage("dev-chat", "장애 발생");

        assertThatCode(() -> notifier.onEscalation(new EscalationEvent("장애 발생")))
                .doesNotThrowAnyException();
    }

    @Test
    void onEscalation_개발자_채팅방_아이디가_없으면_발송하지_않는다() {
        TelegramProperties noDevChat =
                new TelegramProperties("token", "chat", "", 10, 15, 15, AverageMode.SIMPLE, true);
        EscalationNotifier withoutDevChat = new EscalationNotifier(telegramClient, noDevChat);

        withoutDevChat.onEscalation(new EscalationEvent("장애 발생"));

        Mockito.verifyNoInteractions(telegramClient);
    }

    @Test
    void onEscalation_정상_발송되면_그대로_전달한다() {
        notifier.onEscalation(new EscalationEvent("장애 발생"));

        verify(telegramClient).sendMessage("dev-chat", "장애 발생");
    }

    @Test
    void onEscalation_채널_메시지가_있으면_개발자_채팅방과_채널에_각각_보낸다() {
        notifier.onEscalation(new EscalationEvent("상세 오류", "간략 오류"));

        verify(telegramClient).sendMessage("dev-chat", "상세 오류");
        verify(telegramClient).sendMessage("chat", "간략 오류");
    }
}
