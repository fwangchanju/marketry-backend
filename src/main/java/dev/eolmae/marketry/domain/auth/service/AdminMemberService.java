package dev.eolmae.marketry.domain.auth.service;

import dev.eolmae.marketry.domain.auth.dto.AdminMemberPageResponse;
import dev.eolmae.marketry.domain.auth.dto.AdminMemberSummaryResponse;
import dev.eolmae.marketry.domain.auth.enums.Role;
import dev.eolmae.marketry.domain.auth.repository.UserAccountRepository;
import java.time.LocalDateTime;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
@RequiredArgsConstructor
public class AdminMemberService {
    private static final int PAGE_SIZE = 50;
    private static final int RECENT_JOIN_DAYS = 7;
    private static final Set<String> SORT_KEYS = Set.of("id", "email", "nickname", "role", "createdAt", "lastAccessAt");
    private final UserAccountRepository userAccountRepository;

    @Transactional(readOnly = true)
    public AdminMemberPageResponse findMembers(String query, String role, String sort, String order, int page) {
        String keyword = query.trim();
        boolean ascending = "asc".equals(order);
        if (keyword.length() > 100 || page < 0 || !SORT_KEYS.contains(sort) || !(ascending || "desc".equals(order))) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "회원 조회 조건이 올바르지 않습니다.");
        }
        Role selectedRole =
                switch (role) {
                    case "" -> null;
                    case "USER" -> Role.USER;
                    case "ADMIN" -> Role.ADMIN;
                    default -> throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "회원 권한이 올바르지 않습니다.");
                };
        return userAccountRepository.findMembers(keyword, selectedRole, sort, ascending, page, PAGE_SIZE);
    }

    /** 지금 시각을 받아 최근 가입 기준(7일 전)을 정한다. */
    @Transactional(readOnly = true)
    public AdminMemberSummaryResponse getSummary(LocalDateTime now) {
        return userAccountRepository.summarize(now.minusDays(RECENT_JOIN_DAYS));
    }
}
