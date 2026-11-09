package kr.co.marketry.domain.stock.entity;

import jakarta.persistence.Column;
import jakarta.persistence.EmbeddedId;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import lombok.Getter;

@Entity
@Table(name = "overseas_stock_quote")
@Getter
public class OverseasStockQuote {
    @EmbeddedId
    private OverseasStockQuoteId id;

    @Column(nullable = false, precision = 14, scale = 2)
    private BigDecimal currentPrice;

    @Column(nullable = false, precision = 14, scale = 2)
    private BigDecimal lastPrice;

    @Column(nullable = false, precision = 8, scale = 2)
    private BigDecimal changeRate;

    @Column(nullable = false, precision = 20, scale = 0)
    private BigDecimal totalMarketValue;

    @Column(name = "close_verified")
    private Boolean closeVerified;

    protected OverseasStockQuote() {}

    public void changeMarketValue(BigDecimal totalMarketValue) {
        this.totalMarketValue = totalMarketValue;
    }

    public void markCloseVerified() {
        this.closeVerified = true;
    }

    public static OverseasStockQuote create(
            OverseasStockQuoteId id,
            BigDecimal currentPrice,
            BigDecimal lastPrice,
            BigDecimal changeRate,
            BigDecimal totalMarketValue) {
        OverseasStockQuote quote = new OverseasStockQuote();
        quote.id = id;
        quote.currentPrice = currentPrice;
        quote.lastPrice = lastPrice;
        quote.changeRate = changeRate;
        quote.totalMarketValue = totalMarketValue;
        return quote;
    }
}
