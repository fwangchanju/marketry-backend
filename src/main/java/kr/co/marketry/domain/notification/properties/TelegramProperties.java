package kr.co.marketry.domain.notification.properties;

import java.util.ArrayList;
import java.util.List;
import kr.co.marketry.domain.view.enums.AverageMode;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "telegram")
public record TelegramProperties(
        String botToken,
        String chatId,
        String developerChatId,
        int sendMinute,
        int sendIntervalMinutes,
        int beforeMinutes,
        AverageMode averageMode,
        boolean sectorFilter) {

    private static final String CHAT_ID_SEPARATOR = ",";

    // DEVELOPER_CHAT_ID에는 쉼표로 여러 채팅방을 적을 수 있다. 한 계정만 적던 기존 값은 그대로 한 곳이 된다.
    public List<String> developerChatIds() {
        List<String> chatIds = new ArrayList<>();
        if (developerChatId == null) {
            return chatIds;
        }
        for (String chatId : developerChatId.split(CHAT_ID_SEPARATOR, -1)) {
            String stripped = chatId.strip();
            if (stripped.isEmpty() == false) {
                chatIds.add(stripped);
            }
        }
        return chatIds;
    }
}
