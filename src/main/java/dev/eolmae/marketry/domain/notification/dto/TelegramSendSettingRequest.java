package dev.eolmae.marketry.domain.notification.dto;

import java.time.LocalTime;
import java.util.List;

public record TelegramSendSettingRequest(
        boolean enabled, int sectorSendMinute, int sectorSendIntervalMinutes, List<LocalTime> mapSendTimes) {}
