package kr.co.marketry.domain.stock.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import java.io.Serializable;
import java.time.LocalDateTime;
import lombok.EqualsAndHashCode;
import lombok.Getter;

@Embeddable
@Getter
@EqualsAndHashCode
public class OverseasStockQuoteId implements Serializable {
    @Column(nullable = false, length = 16)
    private String market;

    @Column(nullable = false, length = 16)
    private String stockCode;

    @Column(nullable = false)
    private LocalDateTime snapshotTime;

    protected OverseasStockQuoteId() {}

    public OverseasStockQuoteId(String market, String stockCode, LocalDateTime snapshotTime) {
        this.market = market;
        this.stockCode = stockCode;
        this.snapshotTime = snapshotTime;
    }
}
