package kr.co.marketry.domain.stock.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.LocalDateTime;
import lombok.Getter;

/** 시장별 내 기준(초안)을 마지막으로 고친 시각. 시장마다 한 줄이다. */
@Entity
@Table(name = "overseas_classification_draft_edit")
@Getter
public class OverseasClassificationDraftEdit {
    @Id
    @Column(length = 16)
    private String market;

    @Column(nullable = false)
    private LocalDateTime editedAt;

    protected OverseasClassificationDraftEdit() {}

    public static OverseasClassificationDraftEdit create(String market, LocalDateTime editedAt) {
        OverseasClassificationDraftEdit edit = new OverseasClassificationDraftEdit();
        edit.market = market;
        edit.editedAt = editedAt;
        return edit;
    }

    public void overwrite(LocalDateTime editedAt) {
        this.editedAt = editedAt;
    }
}
