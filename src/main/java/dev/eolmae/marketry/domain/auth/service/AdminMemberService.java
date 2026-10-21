package dev.eolmae.marketry.domain.auth.service;

import dev.eolmae.marketry.domain.auth.dto.AdminMemberPageResponse;
import dev.eolmae.marketry.domain.auth.enums.Role;
import dev.eolmae.marketry.domain.auth.repository.UserAccountRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
@RequiredArgsConstructor
public class AdminMemberService {
    private static final int PAGE_SIZE = 50;
    private final UserAccountRepository userAccountRepository;

    @Transactional(readOnly = true)
    public AdminMemberPageResponse findMembers(String query, String role, String sort, int page) {
        String keyword = query.trim();
        if (keyword.length() > 100 || page < 0 || !"createdAt".equals(sort)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "회원 조회 조건이 올바르지 않습니다.");
        }
        Role selectedRole =
                switch (role) {
                    case "" -> null;
                    case "USER" -> Role.USER;
                    case "ADMIN" -> Role.ADMIN;
                    default -> throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "회원 권한이 올바르지 않습니다.");
                };
        return userAccountRepository.findMembers(keyword, selectedRole, page, PAGE_SIZE);
    }
}
