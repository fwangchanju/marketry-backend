package kr.co.marketry.domain.custom.entity;

import jakarta.persistence.Column;
import jakarta.persistence.EmbeddedId;
import jakarta.persistence.Entity;
import jakarta.persistence.PostLoad;
import jakarta.persistence.PostPersist;
import jakarta.persistence.Table;
import jakarta.persistence.Transient;
import java.time.LocalDateTime;
import kr.co.marketry.common.enums.Zone;
import lombok.AccessLevel;
import lombok.Getter;
import org.springframework.data.domain.Persistable;

@Table(name = "custom_stock_sector")
@Entity
@Getter
public class CustomStockSector implements Persistable<CustomStockSectorId> {

    @EmbeddedId
    private CustomStockSectorId id;

    @Column(name = "sector_id", nullable = false)
    private Long sectorId;

    @Column(nullable = false)
    private LocalDateTime createdAt;

    @Column(nullable = false)
    private LocalDateTime updatedAt;

    // id를 앱이 직접 넣는 엔티티라 Spring Data는 새 객체도 기존 행으로 보고 merge한다(저장 전 행마다
    // SELECT). 새로 만든 객체만 persist되게 해서 가입 복제·스냅샷 복원의 saveAll이 배치 INSERT로 나가게 한다.
    @Transient
    @Getter(AccessLevel.NONE)
    private boolean newEntity = true;

    protected CustomStockSector() {}

    public static CustomStockSector create(Long userId, String stockCode, Long sectorId) {
        var entity = new CustomStockSector();
        entity.id = new CustomStockSectorId(userId, stockCode);
        entity.sectorId = sectorId;
        LocalDateTime now = LocalDateTime.now(Zone.KST.zoneId());
        entity.createdAt = now;
        entity.updatedAt = now;
        return entity;
    }

    public Long getUserId() {
        return id.getUserId();
    }

    public String getStockCode() {
        return id.getStockCode();
    }

    public void reassign(Long sectorId) {
        this.sectorId = sectorId;
        this.updatedAt = LocalDateTime.now(Zone.KST.zoneId());
    }

    @Override
    public boolean isNew() {
        return newEntity;
    }

    @PostLoad
    @PostPersist
    void markNotNew() {
        this.newEntity = false;
    }
}
