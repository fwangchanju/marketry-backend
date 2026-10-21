package dev.eolmae.marketry.domain.auth.repository;

import dev.eolmae.marketry.domain.auth.dto.AdminDailyAccessResponse;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Map;

public interface UserAccessDailyRepositoryCustom {

    /**
     * 그날의 접속을 기록한다 — 그날 첫 기록이면 새 행을 만들고, 이미 있으면 마지막 접속 시각과 횟수만 올린다.
     * 같은 회원의 요청이 동시에 와도 한 행만 생기도록 DB가 한 번에 처리한다(PostgreSQL의 ON CONFLICT upsert는 JPQL로 쓸 수 없다).
     */
    void recordAccess(Long userId, LocalDateTime accessedAt);

    /** 실제 가입 회원 중 날짜별로 접속한 서로 다른 회원 수를 센다. 접속한 사람이 없는 날은 빠진다. */
    List<AdminDailyAccessResponse> countMembersByDate(LocalDate from, LocalDate to);

    /** 주어진 회원들이 기간(from~to) 중 접속한 날 수를 센다. 접속한 날이 없는 회원은 결과에 없다. */
    Map<Long, Long> countAccessDaysByUser(Collection<Long> userIds, LocalDate from, LocalDate to);
}
