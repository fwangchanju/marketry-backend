package dev.eolmae.marketry.domain.stock.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;

@Entity
@Table(name = "japan_stock_korean_name")
@Getter
public class JapanStockKoreanName {
    @Id
    private String stockCode;

    @Column(nullable = false, length = 100)
    private String koreanName;

    protected JapanStockKoreanName() {}

    public static JapanStockKoreanName create(String stockCode, String koreanName) {
        JapanStockKoreanName entry = new JapanStockKoreanName();
        entry.stockCode = stockCode;
        entry.koreanName = koreanName;
        return entry;
    }

    public void update(String koreanName) {
        this.koreanName = koreanName;
    }
}
