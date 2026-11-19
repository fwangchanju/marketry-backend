package kr.co.marketry.domain.stock.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import jakarta.persistence.EmbeddedId;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import lombok.EqualsAndHashCode;
import lombok.Getter;

/** 해외 종목의 날짜별 일봉. 종목 차트의 원본이다. */
@Table(name = "overseas_stock_daily")
@Entity
@Getter
public class OverseasStockDaily {
    @EmbeddedId
    private Id id;

    @Column(nullable = false, precision = 14, scale = 2)
    private BigDecimal openPrice;

    @Column(nullable = false, precision = 14, scale = 2)
    private BigDecimal highPrice;

    @Column(nullable = false, precision = 14, scale = 2)
    private BigDecimal lowPrice;

    @Column(nullable = false, precision = 14, scale = 2)
    private BigDecimal closePrice;

    @Column(nullable = false)
    private long volume;

    @Column(nullable = false)
    private LocalDateTime createdAt;

    protected OverseasStockDaily() {}

    public static OverseasStockDaily create(
            String market,
            String stockCode,
            LocalDate tradeDate,
            BigDecimal openPrice,
            BigDecimal highPrice,
            BigDecimal lowPrice,
            BigDecimal closePrice,
            long volume,
            LocalDateTime collectedAt) {
        var entity = new OverseasStockDaily();
        entity.id = new Id(market, stockCode, tradeDate);
        entity.openPrice = openPrice;
        entity.highPrice = highPrice;
        entity.lowPrice = lowPrice;
        entity.closePrice = closePrice;
        entity.volume = volume;
        entity.createdAt = collectedAt;
        return entity;
    }

    @Embeddable
    @Getter
    @EqualsAndHashCode
    public static class Id implements Serializable {
        @Column(nullable = false, length = 16)
        private String market;

        @Column(nullable = false, length = 16)
        private String stockCode;

        @Column(nullable = false)
        private LocalDate tradeDate;

        protected Id() {}

        public Id(String market, String stockCode, LocalDate tradeDate) {
            this.market = market;
            this.stockCode = stockCode;
            this.tradeDate = tradeDate;
        }
    }
}
