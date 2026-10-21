package dev.eolmae.marketry.domain.auth.repository;

import dev.eolmae.marketry.domain.auth.dto.AdminMemberPageResponse;
import dev.eolmae.marketry.domain.auth.enums.Role;

public interface UserAccountRepositoryCustom {
    AdminMemberPageResponse findMembers(String query, Role role, String sort, boolean ascending, int page, int size);
}
