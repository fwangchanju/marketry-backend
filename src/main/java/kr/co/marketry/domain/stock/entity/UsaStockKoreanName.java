package kr.co.marketry.domain.stock.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;

@Entity
@Table(name = "usa_stock_korean_name")
@Getter
public class UsaStockKoreanName {
    @Id
    private String symbol;

    @Column(nullable = false, length = 100)
    private String koreanName;

    protected UsaStockKoreanName() {}

    public static UsaStockKoreanName create(String symbol, String koreanName) {
        UsaStockKoreanName entry = new UsaStockKoreanName();
        entry.symbol = symbol;
        entry.koreanName = koreanName;
        return entry;
    }

    public void update(String koreanName) {
        this.koreanName = koreanName;
    }
}
