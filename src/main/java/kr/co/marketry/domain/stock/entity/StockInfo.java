package kr.co.marketry.domain.stock.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import kr.co.marketry.common.enums.Market;
import kr.co.marketry.common.enums.Zone;
import kr.co.marketry.domain.stock.enums.StockMarketCode;
import lombok.Getter;

@Table(name = "stock_info")
@Entity
@Getter
public class StockInfo {

    @Id
    @Column(length = 20)
    private String stockCode;

    @Column(nullable = false, length = 100)
    private String stockName;

    @Column(nullable = false, length = 20)
    @Enumerated(EnumType.STRING)
    private Market marketType;

    @Column(length = 5)
    private String marketCode;

    @Column(name = "industry_id")
    private Long industryId;

    @Column(nullable = false)
    private Long listCount;

    @Column(precision = 19, scale = 2)
    private BigDecimal lastPrice;

    @Column(nullable = false)
    private boolean active;

    @Column(nullable = false)
    private boolean nxtEnabled;

    @Column(nullable = false)
    private LocalDateTime createdAt;

    @Column(nullable = false)
    private LocalDateTime updatedAt;

    protected StockInfo() {}

    public static StockInfo create(
            String stockCode,
            String stockName,
            Market marketType,
            String marketCode,
            Long industryId,
            Long listCount,
            BigDecimal lastPrice,
            boolean nxtEnabled) {
        var entity = new StockInfo();
        entity.stockCode = stockCode;
        entity.stockName = stockName;
        entity.marketType = marketType;
        entity.marketCode = marketCode;
        entity.industryId = industryId;
        entity.listCount = listCount;
        entity.lastPrice = lastPrice;
        entity.nxtEnabled = nxtEnabled;
        entity.active = true;
        entity.createdAt = LocalDateTime.now(Zone.KST.zoneId());
        entity.updatedAt = LocalDateTime.now(Zone.KST.zoneId());
        return entity;
    }

    public void update(
            String stockName,
            Market marketType,
            String marketCode,
            Long industryId,
            Long listCount,
            BigDecimal lastPrice,
            boolean nxtEnabled) {
        this.stockName = stockName;
        this.marketType = marketType;
        this.marketCode = marketCode;
        this.industryId = industryId;
        this.listCount = listCount;
        this.lastPrice = lastPrice;
        this.nxtEnabled = nxtEnabled;
        this.active = true;
        this.updatedAt = LocalDateTime.now(Zone.KST.zoneId());
    }

    public void markInactive() {
        this.active = false;
        this.updatedAt = LocalDateTime.now(Zone.KST.zoneId());
    }

    /** 마켓맵이 다루는 대상인지: 활성 + 주권(코스피/코스닥)인 종목만 해당. */
    public boolean isActiveAndOrdinary() {
        return active && StockMarketCode.isOrdinaryShare(marketCode);
    }
}
