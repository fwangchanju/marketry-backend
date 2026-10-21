package dev.eolmae.marketry.domain.auth.repository;

import dev.eolmae.marketry.domain.auth.dto.AdminDailyAccessResponse;
import java.time.LocalDate;
import java.util.List;

public interface VisitorDailyRepositoryCustom {

    /**
     * 그날의 방문자를 기록한다 — 이미 기록된 방문자면 아무것도 하지 않는다.
     * 같은 방문자의 요청이 동시에 와도 한 행만 생기도록 DB가 한 번에 처리한다(PostgreSQL의 ON CONFLICT DO NOTHING은 JPQL로 쓸 수 없다).
     */
    void recordVisit(LocalDate visitDate, String visitorKey);

    /** 날짜별 서로 다른 비회원 방문자 수를 센다. 방문자가 없는 날은 빠진다. */
    List<AdminDailyAccessResponse> countVisitorsByDate(LocalDate from, LocalDate to);
}
