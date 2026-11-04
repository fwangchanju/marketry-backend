package dev.eolmae.marketry.domain.stock.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;

@Entity
@Table(name = "overseas_classification_category")
@Getter
public class OverseasClassificationCategory {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 16)
    private String market;

    private Long parentId;

    @Column(nullable = false, length = 12)
    private String name;

    protected OverseasClassificationCategory() {}

    public static OverseasClassificationCategory create(String market, Long parentId, String name) {
        OverseasClassificationCategory category = new OverseasClassificationCategory();
        category.market = market;
        category.parentId = parentId;
        category.name = name;
        return category;
    }

    public void reparent(Long parentId) {
        this.parentId = parentId;
    }

    public void rename(String name) {
        this.name = name;
    }
}
