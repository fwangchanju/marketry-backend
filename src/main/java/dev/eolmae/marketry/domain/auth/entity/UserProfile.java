package dev.eolmae.marketry.domain.auth.entity;

import dev.eolmae.marketry.common.enums.Zone;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.LocalDateTime;
import lombok.Getter;

@Entity
@Table(name = "user_profile")
@Getter
public class UserProfile {

    @Id
    private Long userId;

    @Column(length = 12)
    private String nickname;

    @Column(columnDefinition = "bytea")
    private byte[] image;

    private LocalDateTime imageUpdatedAt;

    private LocalDateTime nicknameChangedAt;

    @Column(nullable = false)
    private LocalDateTime createdAt;

    @Column(nullable = false)
    private LocalDateTime updatedAt;

    protected UserProfile() {}

    public UserProfile(Long userId) {
        LocalDateTime now = LocalDateTime.now(Zone.KST.zoneId());
        this.userId = userId;
        this.createdAt = now;
        this.updatedAt = now;
    }

    /** 닉네임을 바꾼다. 이미 닉네임이 있던 계정의 교체이면 교체 시각을 남기고, 첫 등록이면 남기지 않는다. */
    public void changeNickname(String nickname) {
        LocalDateTime now = LocalDateTime.now(Zone.KST.zoneId());
        if (this.nickname != null) {
            this.nicknameChangedAt = now;
        }
        this.nickname = nickname;
        this.updatedAt = now;
    }

    public void changeImage(byte[] image) {
        LocalDateTime now = LocalDateTime.now(Zone.KST.zoneId());
        this.image = image;
        this.imageUpdatedAt = now;
        this.updatedAt = now;
    }

    public void removeImage() {
        this.image = null;
        this.imageUpdatedAt = null;
        this.updatedAt = LocalDateTime.now(Zone.KST.zoneId());
    }
}
