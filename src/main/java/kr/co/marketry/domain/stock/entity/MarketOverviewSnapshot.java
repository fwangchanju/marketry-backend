package kr.co.marketry.domain.stock.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import kr.co.marketry.common.enums.Market;
import kr.co.marketry.common.enums.Zone;
import lombok.Getter;

@Table(
        name = "market_overview_snapshot",
        uniqueConstraints =
                @UniqueConstraint(
                        name = "uk_market_overview_snapshot",
                        columnNames = {"market_type", "snapshot_time"}))
@Entity
@Getter
public class MarketOverviewSnapshot {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 20)
    @Enumerated(EnumType.STRING)
    private Market marketType;

    @Column(nullable = false)
    private LocalDateTime snapshotTime;

    @Column(nullable = false, precision = 19, scale = 4)
    private BigDecimal indexValue;

    @Column(nullable = false, precision = 19, scale = 4)
    private BigDecimal changeValue;

    @Column(nullable = false, precision = 9, scale = 4)
    private BigDecimal changeRate;

    @Column(nullable = false, precision = 19, scale = 2)
    private BigDecimal tradingValue;

    @Column(nullable = false, length = 30)
    private String marketStatus;

    @Column(nullable = false)
    private int advancers;

    @Column(nullable = false)
    private int decliners;

    @Column(nullable = false)
    private int unchangedCount;

    @Column(nullable = false)
    private int upperLimitCount;

    @Column(nullable = false)
    private int lowerLimitCount;

    @Column(nullable = false)
    private LocalDateTime lastCollectedAt;

    @Column(nullable = false)
    private LocalDateTime createdAt;

    protected MarketOverviewSnapshot() {}

    public static MarketOverviewSnapshot create(
            Market marketType,
            LocalDateTime snapshotTime,
            BigDecimal indexValue,
            BigDecimal changeValue,
            BigDecimal changeRate,
            BigDecimal tradingValue,
            String marketStatus,
            int advancers,
            int decliners,
            int unchangedCount,
            int upperLimitCount,
            int lowerLimitCount,
            LocalDateTime lastCollectedAt) {
        var entity = new MarketOverviewSnapshot();
        entity.marketType = marketType;
        entity.snapshotTime = snapshotTime;
        entity.indexValue = indexValue;
        entity.changeValue = changeValue;
        entity.changeRate = changeRate;
        entity.tradingValue = tradingValue;
        entity.marketStatus = marketStatus;
        entity.advancers = advancers;
        entity.decliners = decliners;
        entity.unchangedCount = unchangedCount;
        entity.upperLimitCount = upperLimitCount;
        entity.lowerLimitCount = lowerLimitCount;
        entity.lastCollectedAt = lastCollectedAt;
        entity.createdAt = LocalDateTime.now(Zone.KST.zoneId());
        return entity;
    }
}
