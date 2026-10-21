package dev.eolmae.marketry.domain.auth.entity;

import jakarta.persistence.EmbeddedId;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Getter;

/**
 * 비회원 방문자의 날짜별 기록 — 방문자 한 명당 하루에 한 줄이다. 행은 {@code VisitorDailyRepository.recordVisit}로만 만들고,
 * 이 엔티티는 집계와 오래된 행 삭제용이다.
 */
@Entity
@Table(name = "visitor_daily")
@Getter
public class VisitorDaily {

    @EmbeddedId
    private VisitorDailyId id;

    protected VisitorDaily() {}
}
