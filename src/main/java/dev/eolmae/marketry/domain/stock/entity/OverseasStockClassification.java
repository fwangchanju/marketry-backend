package dev.eolmae.marketry.domain.stock.entity;

import jakarta.persistence.Column;
import jakarta.persistence.EmbeddedId;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Getter;

@Entity
@Table(name = "overseas_stock_classification")
@Getter
public class OverseasStockClassification {
    @EmbeddedId
    private OverseasStockClassificationId id;

    @Column(length = 12)
    private String sectorName;

    @Column(length = 12)
    private String industryName;

    protected OverseasStockClassification() {}

    public static OverseasStockClassification create(OverseasStockClassificationId id) {
        OverseasStockClassification classification = new OverseasStockClassification();
        classification.id = id;
        return classification;
    }

    public void updateSector(String sectorName) {
        this.sectorName = sectorName;
    }

    public void updateIndustry(String industryName) {
        this.industryName = industryName;
    }
}
