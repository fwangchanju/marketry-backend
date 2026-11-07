package kr.co.marketry.domain.notification.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

public record TelegramRequest(
        @JsonProperty("chat_id") String chatId,
        @JsonProperty("text") String text,
        @JsonProperty("parse_mode") String parseMode) {

    public static TelegramRequest of(String chatId, String text) {
        return new TelegramRequest(chatId, text, "HTML");
    }
}
