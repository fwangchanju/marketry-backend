package dev.eolmae.marketry.domain.custom.entity;

import dev.eolmae.marketry.common.enums.Zone;
import dev.eolmae.marketry.domain.custom.enums.ProtectedSector;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.LocalDateTime;
import lombok.Getter;

@Table(name = "custom_sector")
@Entity
@Getter
public class CustomSector {

    /** 신규 종목이 자동 배정되는 최상위 업종 이름 — 삭제할 수 없다. */
    public static final String NEW_LISTING_NAME = ProtectedSector.NEW_LISTING.getSectorName();

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "parent_id")
    private Long parentId;

    @Column(name = "snapshot_id")
    private Long snapshotId;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(nullable = false, length = 50)
    private String name;

    @Column(nullable = false)
    private int depth;

    @Column(name = "is_excluded", nullable = false)
    private boolean isExcluded;

    @Column(nullable = false)
    private LocalDateTime createdAt;

    @Column(nullable = false)
    private LocalDateTime updatedAt;

    protected CustomSector() {}

    /** 삭제할 수 없는 "신규 상장" 최상위 업종인지. */
    public boolean isNewListing() {
        return parentId == null && NEW_LISTING_NAME.equals(name);
    }

    /** 삭제·이름 변경·이동이 불가능한 보호 업종(신규 상장·거래 정지·미분류)인지 — 최상위에 있는 같은 이름의 업종만 해당한다. */
    public boolean isProtected() {
        return parentId == null && ProtectedSector.isReservedName(name);
    }

    /** 거래 정지 종목이 자동 배정되는 "거래 정지" 업종인지 — 사용자가 직접 종목을 넣을 수 없다. */
    public boolean isSuspended() {
        return parentId == null && ProtectedSector.SUSPENDED.getSectorName().equals(name);
    }

    public static CustomSector createParent(Long userId, String name) {
        var entity = new CustomSector();
        entity.userId = userId;
        entity.name = name;
        entity.parentId = null;
        entity.depth = 0;
        entity.isExcluded = false;
        LocalDateTime now = LocalDateTime.now(Zone.KST.zoneId());
        entity.createdAt = now;
        entity.updatedAt = now;
        return entity;
    }

    public static CustomSector createChild(Long userId, String name, CustomSector parent) {
        var entity = new CustomSector();
        entity.userId = userId;
        entity.name = name;
        entity.parentId = parent.id;
        entity.depth = parent.depth + 1;
        entity.isExcluded = false;
        LocalDateTime now = LocalDateTime.now(Zone.KST.zoneId());
        entity.createdAt = now;
        entity.updatedAt = now;
        return entity;
    }

    public static CustomSector createDefaultParent(Long userId, String name) {
        return createParent(userId, name);
    }

    public void tagSnapshot(Long snapshotId) {
        this.snapshotId = snapshotId;
        this.updatedAt = LocalDateTime.now(Zone.KST.zoneId());
    }

    public void rename(String name) {
        this.name = name;
        this.updatedAt = LocalDateTime.now(Zone.KST.zoneId());
    }

    public void changeParent(Long parentId) {
        this.parentId = parentId;
        this.updatedAt = LocalDateTime.now(Zone.KST.zoneId());
    }

    public void changeDepth(int depth) {
        this.depth = depth;
        this.updatedAt = LocalDateTime.now(Zone.KST.zoneId());
    }

    public void exclude() {
        this.isExcluded = true;
        this.updatedAt = LocalDateTime.now(Zone.KST.zoneId());
    }

    public void include() {
        this.isExcluded = false;
        this.updatedAt = LocalDateTime.now(Zone.KST.zoneId());
    }

    public boolean hasNoParent() {
        return parentId == null;
    }
}
