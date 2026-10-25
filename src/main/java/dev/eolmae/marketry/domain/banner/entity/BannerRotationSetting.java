package dev.eolmae.marketry.domain.banner.entity;

import dev.eolmae.marketry.common.enums.Zone;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.LocalDateTime;
import lombok.Getter;

/** 배너 공개 순환 간격 — 설정 행이 없으면 10초 기본값을 쓴다. */
@Getter
@Entity
@Table(name = "banner_rotation_setting")
public class BannerRotationSetting {
    public static final Long SINGLETON_ID = 1L;

    @Id
    private Long id;

    private int intervalSeconds;

    private LocalDateTime updatedAt;

    protected BannerRotationSetting() {}

    public static BannerRotationSetting create() {
        BannerRotationSetting setting = new BannerRotationSetting();
        setting.id = SINGLETON_ID;
        return setting;
    }

    public void update(int intervalSeconds) {
        this.intervalSeconds = intervalSeconds;
        this.updatedAt = LocalDateTime.now(Zone.KST.zoneId());
    }
}
