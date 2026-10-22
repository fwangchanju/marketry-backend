package dev.eolmae.marketry.domain.notification.dto;

import java.time.LocalDateTime;

/** 화면에 보여 줄 설정과, 입력 칸을 만드는 데 필요한 수집 간격·시간대, 저장된 값이 없을 때 쓰이는 기본값. */
public record TelegramSendSettingResponse(
        boolean enabled,
        int sectorSendMinute,
        int sectorSendIntervalMinutes,
        boolean saved,
        LocalDateTime updatedAt,
        int collectIntervalMinutes,
        int startHour,
        int endHour,
        int defaultSectorSendMinute,
        int defaultSectorSendIntervalMinutes) {}
