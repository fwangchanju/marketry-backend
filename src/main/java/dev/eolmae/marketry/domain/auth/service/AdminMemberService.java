package dev.eolmae.marketry.domain.auth.service;

import dev.eolmae.marketry.domain.auth.dto.AdminMemberPageResponse;
import dev.eolmae.marketry.domain.auth.dto.AdminMemberSpecialResponse;
import dev.eolmae.marketry.domain.auth.dto.AdminMemberSummaryResponse;
import dev.eolmae.marketry.domain.auth.entity.UserAccount;
import dev.eolmae.marketry.domain.auth.enums.MemberGrade;
import dev.eolmae.marketry.domain.auth.enums.Role;
import dev.eolmae.marketry.domain.auth.repository.UserAccountRepository;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
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
    private static final int RECENT_ACCESS_DAYS = 7;
    private static final int MAX_SPECIAL_CHANGE = 100;
    private static final Set<String> SORT_KEYS =
            Set.of("id", "issuer", "email", "nickname", "role", "createdAt", "lastAccessAt");
    private final UserAccountRepository userAccountRepository;

    @Transactional(readOnly = true)
    public AdminMemberPageResponse findMembers(String query, String role, String sort, String order, int page) {
        String keyword = query.trim();
        boolean ascending = "asc".equals(order);
        if (keyword.length() > 100 || page < 0 || !SORT_KEYS.contains(sort) || !(ascending || "desc".equals(order))) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "회원 조회 조건이 올바르지 않습니다.");
        }
        Set<MemberGrade> grades = parseGrades(role);
        return userAccountRepository.findMembers(keyword, grades, sort, ascending, page, PAGE_SIZE);
    }

    /**
     * 체크한 회원을 특별 회원으로 지정하거나 해제한다. 일반 권한(USER)의 실제 가입 회원만 대상이고, 하나라도 대상이 아니면 아무도 바꾸지 않는다.
     */
    @Transactional
    public AdminMemberSpecialResponse changeSpecial(List<Long> userIds, boolean special) {
        if (userIds == null
                || userIds.isEmpty()
                || userIds.size() > MAX_SPECIAL_CHANGE
                || userIds.stream().anyMatch(Objects::isNull)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "회원 선택이 올바르지 않습니다.");
        }
        Set<Long> distinctIds = new HashSet<>(userIds);
        List<UserAccount> users = userAccountRepository.findAllById(distinctIds);
        if (users.size() != distinctIds.size()) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "존재하지 않는 회원이 포함돼 있습니다.");
        }
        boolean hasUnsupportedMember = users.stream()
                .anyMatch(user -> user.getRole() != Role.USER
                        || UserAccount.SYSTEM_ISSUER.equals(user.getIssuer())
                        || UserAccount.LOCAL_TEST_ISSUER.equals(user.getIssuer()));
        if (hasUnsupportedMember) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "일반 회원만 특별 회원으로 바꿀 수 있습니다.");
        }
        users.forEach(user -> user.changeSpecial(special));
        return new AdminMemberSpecialResponse(users.size());
    }

    // 회원 구분은 쉼표로 여러 개를 받는다(예: "USER,SPECIAL"). 비어 있거나 셋 모두면 구분 없이 전체를 본다.
    private Set<MemberGrade> parseGrades(String role) {
        if (role.isEmpty()) {
            return Set.of();
        }
        Set<MemberGrade> grades = EnumSet.noneOf(MemberGrade.class);
        for (String token : Arrays.asList(role.split(",", -1))) {
            try {
                grades.add(MemberGrade.valueOf(token));
            } catch (IllegalArgumentException exception) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "회원 권한이 올바르지 않습니다.");
            }
        }
        return grades.size() == MemberGrade.values().length ? Set.of() : grades;
    }

    /** 지금 시각을 받아 최근 가입·최근 접속 기준(각각 7일 전)을 정한다. */
    @Transactional(readOnly = true)
    public AdminMemberSummaryResponse getSummary(LocalDateTime now) {
        return userAccountRepository.summarize(now.minusDays(RECENT_JOIN_DAYS), now.minusDays(RECENT_ACCESS_DAYS));
    }
}
