package dev.eolmae.marketry.domain.notification.listener;

import dev.eolmae.marketry.common.event.DailyPriceCollectionFailedEvent;
import dev.eolmae.marketry.domain.notification.client.TelegramClient;
import dev.eolmae.marketry.domain.notification.properties.TelegramProperties;
import java.util.ArrayList;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Profile;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@Profile("prod")
@RequiredArgsConstructor
public class DailyPriceCollectionNotifier {
    private static final int MESSAGE_LIMIT = 3000;
    private final TelegramClient telegramClient;
    private final TelegramProperties properties;

    @Async
    @EventListener
    public void onFailure(DailyPriceCollectionFailedEvent event) {
        String chatId = properties.developerChatId();
        if (chatId == null || chatId.isBlank()) {
            return;
        }
        for (String message : createMessages(event)) {
            try {
                telegramClient.sendMessage(chatId, message);
            } catch (RuntimeException failure) {
                log.error(
                        "[일봉 누락 알림 실패] | context : {}|{}",
                        event.targetDate(),
                        failure.getClass().getSimpleName());
            }
        }
    }

    static List<String> createMessages(DailyPriceCollectionFailedEvent event) {
        String header = "[일봉 수집 미완료] " + event.targetDate() + "\n" + event.reason() + "\n";
        List<String> messages = new ArrayList<>();
        var message = new StringBuilder(header);
        // Telegram의 HTML 해석과 길이 제한 때문에 종목명을 이스케이프하고 목록을 나눈다.
        for (String stock : event.missingStocks()) {
            String line = stock.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;") + "\n";
            if (message.length() + line.length() > MESSAGE_LIMIT) {
                messages.add(message.toString());
                message = new StringBuilder(header);
            }
            message.append(line);
        }
        messages.add(message.toString());
        return messages;
    }
}
