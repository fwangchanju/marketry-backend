package kr.co.marketry.domain.custom.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.LocalDateTime;
import kr.co.marketry.common.enums.Zone;
import lombok.Getter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

// 사용자별 화면 환경설정(showValue 등) 전체를 하나의 JSONB로 담는다 — 필드가 늘 때마다 컬럼을
// 추가하지 않기 위해서다(CustomSnapshot.snapshotJson과 같은 패턴).
@Table(name = "user_preference")
@Entity
@Getter
public class UserPreference {

    @Id
    @Column(name = "user_id")
    private Long userId;

    @Column(nullable = false)
    @JdbcTypeCode(SqlTypes.JSON)
    private String payload;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    protected UserPreference() {}

    public static UserPreference createEmpty(Long userId) {
        var entity = new UserPreference();
        entity.userId = userId;
        entity.payload = "{}";
        entity.updatedAt = LocalDateTime.now(Zone.KST.zoneId());
        return entity;
    }

    public void overwrite(String payload) {
        this.payload = payload;
        this.updatedAt = LocalDateTime.now(Zone.KST.zoneId());
    }
}
