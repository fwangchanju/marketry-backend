package kr.co.marketry.domain.stock.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import jakarta.persistence.EmbeddedId;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import java.io.Serializable;
import java.time.LocalDate;
import java.time.LocalDateTime;
import lombok.EqualsAndHashCode;
import lombok.Getter;

/** 나눠 받는 과거 데이터 수집에서 끝난 항목. 다시 실행하면 이 항목은 호출하지 않는다. */
@Table(name = "history_backfill_progress")
@Entity
@Getter
public class HistoryBackfillProgress {
    @EmbeddedId
    private Id id;

    @Column(nullable = false)
    private int savedRows;

    private LocalDate oldestDate;

    @Column(nullable = false)
    private LocalDateTime completedAt;

    protected HistoryBackfillProgress() {}

    public static HistoryBackfillProgress create(
            String job, String item, int savedRows, LocalDate oldestDate, LocalDateTime completedAt) {
        var entity = new HistoryBackfillProgress();
        entity.id = new Id(job, item);
        entity.savedRows = savedRows;
        entity.oldestDate = oldestDate;
        entity.completedAt = completedAt;
        return entity;
    }

    @Embeddable
    @Getter
    @EqualsAndHashCode
    public static class Id implements Serializable {
        @Column(nullable = false, length = 32)
        private String job;

        @Column(nullable = false, length = 20)
        private String item;

        protected Id() {}

        public Id(String job, String item) {
            this.job = job;
            this.item = item;
        }
    }
}
