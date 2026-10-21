package dev.eolmae.marketry.domain.auth.repository;

import dev.eolmae.marketry.domain.auth.dto.AdminMemberPageResponse;
import dev.eolmae.marketry.domain.auth.dto.AdminMemberSummaryResponse;
import dev.eolmae.marketry.domain.auth.enums.MemberGrade;
import java.time.LocalDateTime;
import java.util.Set;

public interface UserAccountRepositoryCustom {
    AdminMemberPageResponse findMembers(
            String query, Set<MemberGrade> grades, String sort, boolean ascending, int page, int size);

    AdminMemberSummaryResponse summarize(LocalDateTime joinedSince, LocalDateTime accessedSince);
}
