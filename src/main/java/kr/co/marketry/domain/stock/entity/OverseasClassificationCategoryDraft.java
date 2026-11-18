package kr.co.marketry.domain.stock.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;

/** 일본·미국 내 기준(초안)의 업종. liveId는 이 초안 업종이 복사해 온 운영 업종의 id이고, 초안에서 새로 만든 업종은 null이다. */
@Entity
@Table(name = "overseas_classification_category_draft")
@Getter
public class OverseasClassificationCategoryDraft {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 16)
    private String market;

    private Long liveId;

    private Long parentId;

    @Column(nullable = false, length = 40)
    private String name;

    protected OverseasClassificationCategoryDraft() {}

    public static OverseasClassificationCategoryDraft create(String market, Long liveId, Long parentId, String name) {
        OverseasClassificationCategoryDraft category = new OverseasClassificationCategoryDraft();
        category.market = market;
        category.liveId = liveId;
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

    public void linkLive(Long liveId) {
        this.liveId = liveId;
    }
}
