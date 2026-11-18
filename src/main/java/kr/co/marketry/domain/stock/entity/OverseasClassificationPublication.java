package kr.co.marketry.domain.stock.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.LocalDateTime;
import lombok.Getter;

/** 시장별 MARKETRY 분류를 마지막으로 업데이트(또는 롤백)한 시각. 시장마다 한 줄이다. */
@Entity
@Table(name = "overseas_classification_publication")
@Getter
public class OverseasClassificationPublication {
    @Id
    @Column(length = 16)
    private String market;

    @Column(nullable = false)
    private LocalDateTime publishedAt;

    protected OverseasClassificationPublication() {}

    public static OverseasClassificationPublication create(String market, LocalDateTime publishedAt) {
        OverseasClassificationPublication publication = new OverseasClassificationPublication();
        publication.market = market;
        publication.publishedAt = publishedAt;
        return publication;
    }

    public void overwrite(LocalDateTime publishedAt) {
        this.publishedAt = publishedAt;
    }
}
