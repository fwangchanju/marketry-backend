package kr.co.marketry.domain.stock.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;

/** 키움이 업종명을 비워 보내는 종목의 거래소 분류 보정. 키움 업종이 없을 때만 쓴다. */
@Entity
@Table(name = "stock_industry_override")
@Getter
public class StockIndustryOverride {

    @Id
    @Column(name = "stock_code", length = 20)
    private String stockCode;

    @Column(name = "industry_id", nullable = false)
    private Long industryId;

    protected StockIndustryOverride() {}

    public static StockIndustryOverride create(String stockCode, Long industryId) {
        var override = new StockIndustryOverride();
        override.stockCode = stockCode;
        override.industryId = industryId;
        return override;
    }
}
