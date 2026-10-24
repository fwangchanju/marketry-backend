package dev.eolmae.marketry.domain.notification.properties;

import dev.eolmae.marketry.domain.view.enums.AverageMode;
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
        boolean sectorFilter) {}
