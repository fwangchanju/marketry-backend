package dev.eolmae.marketry.domain.auth.repository;

import dev.eolmae.marketry.domain.auth.dto.AdminDailyAccessResponse;
import dev.eolmae.marketry.domain.auth.dto.AdminVisitorSourceResponse;
import dev.eolmae.marketry.domain.auth.enums.VisitorSource;
import java.time.LocalDate;
import java.util.List;

public interface VisitorDailyRepositoryCustom {

    /**
     * 그날의 방문자를 기록한다 — 이미 기록된 방문자면 아무것도 하지 않는다.
     * 같은 방문자의 요청이 동시에 와도 한 행만 생기도록 DB가 한 번에 처리하며, 출처는 그날 처음 기록한 값이 남는다(PostgreSQL의 ON CONFLICT DO NOTHING은 JPQL로 쓸 수 없다).
     */
    void recordVisit(LocalDate visitDate, String visitorKey, VisitorSource source);

    /** 기간 안의 비회원 방문을 출처별로 센다. 출처를 기록하기 전의 행은 빠지고, 방문이 없는 출처도 빠진다. */
    List<AdminVisitorSourceResponse> countVisitorsBySource(LocalDate from, LocalDate to);

    /** 날짜별 서로 다른 비회원 방문자 수를 센다. 방문자가 없는 날은 빠진다. */
    List<AdminDailyAccessResponse> countVisitorsByDate(LocalDate from, LocalDate to);
}
