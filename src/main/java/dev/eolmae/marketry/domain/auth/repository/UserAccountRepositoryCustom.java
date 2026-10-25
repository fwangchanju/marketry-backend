package dev.eolmae.marketry.domain.auth.repository;

import dev.eolmae.marketry.domain.auth.dto.AdminDailyAccessResponse;
import dev.eolmae.marketry.domain.auth.dto.AdminMemberPageResponse;
import dev.eolmae.marketry.domain.auth.dto.AdminMemberSummaryResponse;
import dev.eolmae.marketry.domain.auth.enums.MemberGrade;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;

public interface UserAccountRepositoryCustom {
    AdminMemberPageResponse findMembers(
            String query, Set<MemberGrade> grades, String sort, boolean ascending, int page, int size);

    AdminMemberPageResponse findMembers(
            String query, Set<MemberGrade> grades, Set<String> issuers, String sort, boolean ascending, int page, int size);

    AdminMemberSummaryResponse summarize(LocalDateTime joinedSince, LocalDateTime accessedSince);

    /** 실제 가입 회원 중 날짜별 신규 가입 수(from~to). 가입한 사람이 없는 날은 빠진다. */
    List<AdminDailyAccessResponse> countJoinedByDate(LocalDate from, LocalDate to);

    /** 실제 가입 회원 중 주어진 시각 전에 가입한 회원 수. */
    long countJoinedBefore(LocalDateTime before);

    /** 실제 가입 회원 수(관리자·특별 회원·일반 회원 합계). */
    long countMembers();
}
