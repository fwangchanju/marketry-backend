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

/** 지수의 날짜별 값. 5분 단위 시장 요약과 달리 상승·하락 종목 수 없이 과거까지 담는다. */
@Table(name = "market_index_daily")
@Entity
@Getter
public class MarketIndexDaily {
    @EmbeddedId
    private Id id;

    @Column(nullable = false, precision = 14, scale = 2)
    private BigDecimal openValue;

    @Column(nullable = false, precision = 14, scale = 2)
    private BigDecimal highValue;

    @Column(nullable = false, precision = 14, scale = 2)
    private BigDecimal lowValue;

    @Column(nullable = false, precision = 14, scale = 2)
    private BigDecimal closeValue;

    @Column(nullable = false)
    private long volume;

    private Long tradingValue;

    @Column(nullable = false)
    private LocalDateTime createdAt;

    protected MarketIndexDaily() {}

    public static MarketIndexDaily create(
            String market,
            LocalDate tradeDate,
            BigDecimal openValue,
            BigDecimal highValue,
            BigDecimal lowValue,
            BigDecimal closeValue,
            long volume,
            Long tradingValue,
            LocalDateTime collectedAt) {
        var entity = new MarketIndexDaily();
        entity.id = new Id(market, tradeDate);
        entity.openValue = openValue;
        entity.highValue = highValue;
        entity.lowValue = lowValue;
        entity.closeValue = closeValue;
        entity.volume = volume;
        entity.tradingValue = tradingValue;
        entity.createdAt = collectedAt;
        return entity;
    }

    @Embeddable
    @Getter
    @EqualsAndHashCode
    public static class Id implements Serializable {
        @Column(nullable = false, length = 16)
        private String market;

        @Column(nullable = false)
        private LocalDate tradeDate;

        protected Id() {}

        public Id(String market, LocalDate tradeDate) {
            this.market = market;
            this.tradeDate = tradeDate;
        }
    }
}
