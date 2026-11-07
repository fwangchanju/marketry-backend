package kr.co.marketry.domain.notification.dto;

public record TelegramSendSettingRequest(boolean enabled, int sectorSendMinute, int sectorSendIntervalMinutes) {}
