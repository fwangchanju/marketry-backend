package kr.co.marketry.domain.stock.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.LocalDateTime;
import lombok.Getter;

/** MARKETRY에 반영하기 직전 운영 분류 전체를 JSON 글로 적어 둔 백업. 시장마다 가장 최근 한 번분만 남긴다. */
@Entity
@Table(name = "overseas_classification_backup")
@Getter
public class OverseasClassificationBackup {
    @Id
    @Column(length = 16)
    private String market;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String payload;

    @Column(nullable = false)
    private LocalDateTime createdAt;

    protected OverseasClassificationBackup() {}

    public static OverseasClassificationBackup create(String market, String payload, LocalDateTime createdAt) {
        OverseasClassificationBackup backup = new OverseasClassificationBackup();
        backup.market = market;
        backup.payload = payload;
        backup.createdAt = createdAt;
        return backup;
    }

    public void overwrite(String payload, LocalDateTime createdAt) {
        this.payload = payload;
        this.createdAt = createdAt;
    }
}
