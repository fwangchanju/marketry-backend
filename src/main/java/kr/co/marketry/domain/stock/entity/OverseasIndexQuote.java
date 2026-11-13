package kr.co.marketry.domain.stock.entity;

import jakarta.persistence.Column;
import jakarta.persistence.EmbeddedId;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import lombok.Getter;

/** 해외 지수의 5분 시점 값. 종목 시세와 같은 시각(snapshotTime)으로 저장해 그래프가 같은 시점끼리 비교한다. */
@Entity
@Table(name = "overseas_index_quote")
@Getter
public class OverseasIndexQuote {
    @EmbeddedId
    private OverseasIndexQuoteId id;

    @Column(nullable = false, precision = 14, scale = 2)
    private BigDecimal currentValue;

    @Column(nullable = false, precision = 14, scale = 2)
    private BigDecimal lastValue;

    @Column(nullable = false, precision = 8, scale = 2)
    private BigDecimal changeRate;

    protected OverseasIndexQuote() {}

    public static OverseasIndexQuote create(
            OverseasIndexQuoteId id, BigDecimal currentValue, BigDecimal lastValue, BigDecimal changeRate) {
        OverseasIndexQuote quote = new OverseasIndexQuote();
        quote.id = id;
        quote.currentValue = currentValue;
        quote.lastValue = lastValue;
        quote.changeRate = changeRate;
        return quote;
    }
}
