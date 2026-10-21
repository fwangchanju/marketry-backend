package dev.eolmae.marketry.domain.auth.entity;

import jakarta.persistence.Column;
import jakarta.persistence.EmbeddedId;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import java.time.LocalDateTime;
import lombok.Getter;

/**
 * 회원별·날짜별 접속 기록 — 하루에 한 줄이다. 행은 {@code UserAccessDailyRepository.recordAccess}(upsert)로만 만든다.
 * 이 엔티티는 읽기·집계와 삭제용이다.
 */
@Entity
@Table(name = "user_access_daily")
@Getter
public class UserAccessDaily {

    @EmbeddedId
    private UserAccessDailyId id;

    @Column(nullable = false)
    private LocalDateTime firstAccessAt;

    @Column(nullable = false)
    private LocalDateTime lastAccessAt;

    @Column(nullable = false)
    private int accessCount;

    protected UserAccessDaily() {}
}
