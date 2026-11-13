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
public class OverseasIndexQuoteId implements Serializable {
    @Column(nullable = false, length = 16)
    private String market;

    @Column(nullable = false)
    private LocalDateTime snapshotTime;

    protected OverseasIndexQuoteId() {}

    public OverseasIndexQuoteId(String market, LocalDateTime snapshotTime) {
        this.market = market;
        this.snapshotTime = snapshotTime;
    }
}
