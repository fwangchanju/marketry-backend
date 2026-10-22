package dev.eolmae.marketry.domain.notification.dto;

public record TelegramSendSettingRequest(boolean enabled, int sectorSendMinute, int sectorSendIntervalMinutes) {}
