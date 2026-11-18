package kr.co.marketry.domain.stock.entity;

import jakarta.persistence.Column;
import jakarta.persistence.EmbeddedId;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Getter;

/** 일본·미국 내 기준(초안)의 종목 분류. 운영 종목 분류와 같은 모양이다. */
@Entity
@Table(name = "overseas_stock_classification_draft")
@Getter
public class OverseasStockClassificationDraft {
    @EmbeddedId
    private OverseasStockClassificationId id;

    @Column(length = 40)
    private String sectorName;

    @Column(length = 40)
    private String industryName;

    @Column(length = 40)
    private String subIndustryName;

    protected OverseasStockClassificationDraft() {}

    public static OverseasStockClassificationDraft create(OverseasStockClassificationId id) {
        OverseasStockClassificationDraft classification = new OverseasStockClassificationDraft();
        classification.id = id;
        return classification;
    }

    public void updateSector(String sectorName) {
        this.sectorName = sectorName;
    }

    public void updateIndustry(String industryName) {
        this.industryName = industryName;
    }

    public void updateSubIndustry(String subIndustryName) {
        this.subIndustryName = subIndustryName;
    }
}
