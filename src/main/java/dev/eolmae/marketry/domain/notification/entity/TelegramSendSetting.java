package dev.eolmae.marketry.domain.notification.entity;

import dev.eolmae.marketry.common.enums.Zone;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;
import lombok.Getter;

/** 텔레그램 발송 설정 — 행 하나만 쓴다({@link #SINGLETON_ID}). 행이 없으면 서버 설정값이 기본이다. */
@Getter
@Entity
@Table(name = "telegram_send_setting")
public class TelegramSendSetting {
    public static final Long SINGLETON_ID = 1L;
    private static final String TIME_SEPARATOR = ",";

    @Id
    private Long id;

    @Column(nullable = false)
    private boolean enabled;

    @Column(nullable = false)
    private int sectorSendMinute;

    @Column(nullable = false)
    private int sectorSendIntervalMinutes;

    @Column(nullable = false, length = 200)
    private String mapSendTimes;

    @Column(nullable = false)
    private LocalDateTime updatedAt;

    protected TelegramSendSetting() {}

    public static TelegramSendSetting create() {
        TelegramSendSetting setting = new TelegramSendSetting();
        setting.id = SINGLETON_ID;
        return setting;
    }

    public void update(
            boolean enabled, int sectorSendMinute, int sectorSendIntervalMinutes, List<LocalTime> mapSendTimes) {
        this.enabled = enabled;
        this.sectorSendMinute = sectorSendMinute;
        this.sectorSendIntervalMinutes = sectorSendIntervalMinutes;
        this.mapSendTimes = mapSendTimes.stream().map(LocalTime::toString).collect(Collectors.joining(TIME_SEPARATOR));
        this.updatedAt = LocalDateTime.now(Zone.KST.zoneId());
    }

    public List<LocalTime> mapSendTimeList() {
        return Arrays.stream(mapSendTimes.split(TIME_SEPARATOR))
                .map(LocalTime::parse)
                .toList();
    }
}
