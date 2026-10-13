package dev.eolmae.marketry.domain.stock.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.SequenceGenerator;
import jakarta.persistence.Table;
import java.time.LocalDate;
import java.time.LocalDateTime;
import lombok.Getter;

/**
 * 한 종목의 거래정지가 이어진 기간. 연속 5거래일에 이른 날({@code excludedFrom})부터 재개된 날({@code endedOn}) 전까지는
 * 지도와 평균에서 뺀다. 재개되면 {@code endedOn}을 채우므로 그날부터 가격이 다시 들어온다.
 */
@Table(name = "stock_trading_suspension")
@Entity
@Getter
public class StockTradingSuspension {
    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "stock_trading_suspension_id_seq")
    @SequenceGenerator(
            name = "stock_trading_suspension_id_seq",
            sequenceName = "stock_trading_suspension_id_seq",
            allocationSize = 50)
    private Long id;

    @Column(nullable = false, length = 20)
    private String stockCode;

    @Column(nullable = false)
    private LocalDate startedOn;

    private LocalDate excludedFrom;

    private LocalDate endedOn;

    @Column(nullable = false)
    private LocalDateTime createdAt;

    @Column(nullable = false)
    private LocalDateTime updatedAt;

    protected StockTradingSuspension() {}

    public static StockTradingSuspension start(String stockCode, LocalDate startedOn, LocalDateTime now) {
        var entity = new StockTradingSuspension();
        entity.stockCode = stockCode;
        entity.startedOn = startedOn;
        entity.createdAt = now;
        entity.updatedAt = now;
        return entity;
    }

    /** 연속 5거래일에 이른 날부터 제외한다. 이미 제외 중이면 처음 정한 날을 유지한다. */
    public void markExcluded(LocalDate date, LocalDateTime now) {
        if (excludedFrom != null) {
            return;
        }
        this.excludedFrom = date;
        this.updatedAt = now;
    }

    /** 거래정지가 풀린 날을 기록한다 — 그날부터는 평균과 지도에 다시 들어간다. */
    public void end(LocalDate date, LocalDateTime now) {
        this.endedOn = date;
        this.updatedAt = now;
    }

    public boolean isOpen() {
        return endedOn == null;
    }

    public boolean isExcludedOn(LocalDate date) {
        return excludedFrom != null && !date.isBefore(excludedFrom) && (endedOn == null || date.isBefore(endedOn));
    }
}
