package kr.co.marketry.domain.stock.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import java.io.Serializable;
import lombok.EqualsAndHashCode;
import lombok.Getter;

@Embeddable
@Getter
@EqualsAndHashCode
public class OverseasStockClassificationId implements Serializable {
    @Column(nullable = false, length = 16)
    private String market;

    @Column(nullable = false, length = 16)
    private String stockCode;

    protected OverseasStockClassificationId() {}

    public OverseasStockClassificationId(String market, String stockCode) {
        this.market = market;
        this.stockCode = stockCode;
    }
}
