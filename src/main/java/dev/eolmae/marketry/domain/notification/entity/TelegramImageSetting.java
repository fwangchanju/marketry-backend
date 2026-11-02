package dev.eolmae.marketry.domain.notification.entity;

import dev.eolmae.marketry.common.enums.Zone;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.LocalDateTime;
import lombok.Getter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Entity
@Table(name = "telegram_image_setting")
@Getter
public class TelegramImageSetting {
    @Id
    private Long id;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(nullable = false)
    private String payload;

    @Column(nullable = false)
    private LocalDateTime updatedAt;

    protected TelegramImageSetting() {}

    public static TelegramImageSetting create() {
        TelegramImageSetting setting = new TelegramImageSetting();
        setting.id = 1L;
        return setting;
    }

    public void update(String payload) {
        this.payload = payload;
        this.updatedAt = LocalDateTime.now(Zone.KST.zoneId());
    }
}
