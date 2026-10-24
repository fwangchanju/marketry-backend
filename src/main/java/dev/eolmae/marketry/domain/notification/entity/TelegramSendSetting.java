package dev.eolmae.marketry.domain.notification.entity;

import dev.eolmae.marketry.common.enums.Zone;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.LocalDateTime;
import lombok.Getter;

/** 텔레그램 발송 설정 — 행 하나만 쓴다({@link #SINGLETON_ID}). 행이 없으면 서버 설정값이 기본이다. */
@Getter
@Entity
@Table(name = "telegram_send_setting")
public class TelegramSendSetting {
    public static final Long SINGLETON_ID = 1L;

    @Id
    private Long id;

    @Column(nullable = false)
    private boolean enabled;

    @Column(nullable = false)
    private int sectorSendMinute;

    @Column(nullable = false)
    private int sectorSendIntervalMinutes;

    // 지도를 따로 보내던 때의 칸이다. NOT NULL이고 마이그레이션 가드가 DROP을 막아 칸을 남겼다 — 처음 만들 때 빈 값만 채우고 더는 쓰지 않는다.
    @Column(nullable = false, length = 200)
    private String mapSendTimes;

    @Column(nullable = false)
    private LocalDateTime updatedAt;

    protected TelegramSendSetting() {}

    public static TelegramSendSetting create() {
        TelegramSendSetting setting = new TelegramSendSetting();
        setting.id = SINGLETON_ID;
        setting.mapSendTimes = "";
        return setting;
    }

    public void update(boolean enabled, int sectorSendMinute, int sectorSendIntervalMinutes) {
        this.enabled = enabled;
        this.sectorSendMinute = sectorSendMinute;
        this.sectorSendIntervalMinutes = sectorSendIntervalMinutes;
        this.updatedAt = LocalDateTime.now(Zone.KST.zoneId());
    }
}
