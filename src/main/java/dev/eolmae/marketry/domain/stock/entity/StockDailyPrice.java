package dev.eolmae.marketry.domain.stock.entity;

import dev.eolmae.marketry.domain.stock.enums.ExchangeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.SequenceGenerator;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import lombok.Getter;

/** ka11081 원주가(KRW) 일봉. 거래정지 일봉의 0은 보존하며 등락률은 조회 시 계산한다. */
@Table(
        name = "stock_daily_price",
        uniqueConstraints =
                @UniqueConstraint(
                        name = "uk_stock_daily_price",
                        columnNames = {"stock_code", "exchange_type", "trade_date"}))
@Entity
@Getter
public class StockDailyPrice {
    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "stock_daily_price_id_seq")
    @SequenceGenerator(
            name = "stock_daily_price_id_seq",
            sequenceName = "stock_daily_price_id_seq",
            allocationSize = 500)
    private Long id;

    @Column(nullable = false, length = 20)
    private String stockCode;

    @Column(nullable = false, length = 20)
    @Enumerated(EnumType.STRING)
    private ExchangeType exchangeType;

    @Column(nullable = false)
    private LocalDate tradeDate;

    @Column(nullable = false, precision = 19, scale = 4)
    private BigDecimal openPrice;

    @Column(nullable = false, precision = 19, scale = 4)
    private BigDecimal highPrice;

    @Column(nullable = false, precision = 19, scale = 4)
    private BigDecimal lowPrice;

    @Column(nullable = false, precision = 19, scale = 4)
    private BigDecimal closePrice;

    @Column(nullable = false)
    private long volume;

    @Column(nullable = false)
    private LocalDateTime createdAt;

    protected StockDailyPrice() {}

    public static StockDailyPrice create(
            String stockCode,
            ExchangeType exchangeType,
            LocalDate tradeDate,
            BigDecimal openPrice,
            BigDecimal highPrice,
            BigDecimal lowPrice,
            BigDecimal closePrice,
            long volume,
            LocalDateTime collectedAt) {
        var entity = new StockDailyPrice();
        entity.stockCode = stockCode;
        entity.exchangeType = exchangeType;
        entity.tradeDate = tradeDate;
        entity.openPrice = openPrice;
        entity.highPrice = highPrice;
        entity.lowPrice = lowPrice;
        entity.closePrice = closePrice;
        entity.volume = volume;
        entity.createdAt = collectedAt;
        return entity;
    }

    public boolean hasSamePrices(StockDailyPrice other) {
        return openPrice.compareTo(other.openPrice) == 0
                && highPrice.compareTo(other.highPrice) == 0
                && lowPrice.compareTo(other.lowPrice) == 0
                && closePrice.compareTo(other.closePrice) == 0
                && volume == other.volume;
    }
}
