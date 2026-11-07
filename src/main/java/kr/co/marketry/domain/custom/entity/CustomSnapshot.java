package kr.co.marketry.domain.custom.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.LocalDateTime;
import kr.co.marketry.common.enums.Zone;
import lombok.Getter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Table(name = "custom_snapshot")
@Entity
@Getter
public class CustomSnapshot {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 50)
    private String label;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(name = "snapshot_json", nullable = false)
    @JdbcTypeCode(SqlTypes.JSON)
    private String snapshotJson;

    @Column(nullable = false)
    private LocalDateTime createdAt;

    @Column(nullable = false)
    private LocalDateTime updatedAt;

    protected CustomSnapshot() {}

    public static CustomSnapshot create(Long userId, String label, String snapshotJson) {
        var entity = new CustomSnapshot();
        entity.userId = userId;
        entity.label = label;
        entity.snapshotJson = snapshotJson;
        LocalDateTime now = LocalDateTime.now(Zone.KST.zoneId());
        entity.createdAt = now;
        entity.updatedAt = now;
        return entity;
    }

    public void overwrite(String label, String snapshotJson) {
        this.label = label;
        this.snapshotJson = snapshotJson;
        this.updatedAt = LocalDateTime.now(Zone.KST.zoneId());
    }
}
