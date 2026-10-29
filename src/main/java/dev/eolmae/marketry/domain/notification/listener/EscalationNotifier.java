package dev.eolmae.marketry.domain.notification.listener;

import dev.eolmae.marketry.common.event.EscalationEvent;
import dev.eolmae.marketry.domain.notification.client.TelegramClient;
import dev.eolmae.marketry.domain.notification.properties.TelegramProperties;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Profile;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

/** 개발자(DEVELOPER_CHAT_ID)에게 에스컬레이션 알림을 발송하는 리스너. {@link EscalationEvent} 수신 → 텔레그램. */
@Slf4j
@Component
@Profile("prod")
@RequiredArgsConstructor
public class EscalationNotifier {

    private final TelegramClient telegramClient;
    private final TelegramProperties properties;

    @EventListener
    public void onEscalation(EscalationEvent event) {
        String chatId = properties.developerChatId();
        if (chatId != null && chatId.isBlank() == false) {
            sendBestEffort(chatId, event.message());
        }
        if (event.channelMessage() != null
                && properties.chatId() != null
                && properties.chatId().isBlank() == false) {
            sendBestEffort(properties.chatId(), event.channelMessage());
        }
    }

    private void sendBestEffort(String chatId, String message) {
        // 알림은 best-effort다 — 여기서 예외가 나면 @EventListener가 동기라 호출부로 역류해 원래 처리가 중단된다.
        try {
            telegramClient.sendMessage(chatId, message);
        } catch (Exception e) {
            log.error("에스컬레이션 알림 발송에 실패했습니다 | chatId={} | message : {}", chatId, message, e);
        }
    }
}
