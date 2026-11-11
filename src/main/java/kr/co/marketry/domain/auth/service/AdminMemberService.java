package kr.co.marketry.domain.auth.service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import kr.co.marketry.common.enums.Zone;
import kr.co.marketry.domain.auth.dto.AdminAccessFrequencyResponse;
import kr.co.marketry.domain.auth.dto.AdminBotDayCountResponse;
import kr.co.marketry.domain.auth.dto.AdminBotVisitResponse;
import kr.co.marketry.domain.auth.dto.AdminDailyAccessResponse;
import kr.co.marketry.domain.auth.dto.AdminMemberNicknameResetResponse;
import kr.co.marketry.domain.auth.dto.AdminMemberPageResponse;
import kr.co.marketry.domain.auth.dto.AdminMemberResponse;
import kr.co.marketry.domain.auth.dto.AdminMemberSpecialResponse;
import kr.co.marketry.domain.auth.dto.AdminMemberSummaryResponse;
import kr.co.marketry.domain.auth.dto.AdminSignupDailyResponse;
import kr.co.marketry.domain.auth.dto.AdminVisitorDailyResponse;
import kr.co.marketry.domain.auth.dto.AdminVisitorSourceResponse;
import kr.co.marketry.domain.auth.entity.UserAccount;
import kr.co.marketry.domain.auth.entity.UserProfile;
import kr.co.marketry.domain.auth.enums.MemberGrade;
import kr.co.marketry.domain.auth.enums.Role;
import kr.co.marketry.domain.auth.enums.VisitorSource;
import kr.co.marketry.domain.auth.repository.UserAccessDailyRepository;
import kr.co.marketry.domain.auth.repository.UserAccountRepository;
import kr.co.marketry.domain.auth.repository.UserProfileRepository;
import kr.co.marketry.domain.auth.repository.VisitorDailyRepository;
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
    private static final int MAX_DAILY_ACCESS_DAYS = 90;
    private static final int ACCESS_DAYS_WINDOW = 30;
    private static final int MAX_SIGNUP_DAYS = 180;
    private static final int MAX_SPECIAL_CHANGE = 100;
    private static final String NO_PROVIDER_FILTER = "NONE";
    private static final Set<String> MEMBER_ISSUER_FILTERS = Set.of(
            "https://nid.naver.com", "https://kauth.kakao.com", "https://accounts.google.com", NO_PROVIDER_FILTER);
    private static final String[] ACCESS_FREQUENCY_LABELS = {"0일", "1~2일", "3~6일", "7~14일", "15일 이상"};
    private static final Set<String> SORT_KEYS =
            Set.of("id", "issuer", "email", "nickname", "role", "createdAt", "lastAccessAt");
    private final UserAccountRepository userAccountRepository;
    private final UserAccessDailyRepository userAccessDailyRepository;
    private final UserProfileRepository userProfileRepository;
    private final VisitorDailyRepository visitorDailyRepository;

    @Transactional(readOnly = true)
    public AdminMemberPageResponse findMembers(String query, String role, String sort, String order, int page) {
        return findMembers(query, role, "", sort, order, page);
    }

    @Transactional(readOnly = true)
    public AdminMemberPageResponse findMembers(
            String query, String role, String issuer, String sort, String order, int page) {
        String keyword = query.trim();
        boolean ascending = "asc".equals(order);
        if (keyword.length() > 100 || page < 0 || !SORT_KEYS.contains(sort) || !(ascending || "desc".equals(order))) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "회원 조회 조건이 올바르지 않습니다.");
        }
        Set<MemberGrade> grades = parseGrades(role);
        Set<String> issuers = parseIssuers(issuer);
        AdminMemberPageResponse result = issuers.isEmpty()
                ? userAccountRepository.findMembers(keyword, grades, sort, ascending, page, PAGE_SIZE)
                : userAccountRepository.findMembers(keyword, grades, issuers, sort, ascending, page, PAGE_SIZE);
        return withAccessDays(result, LocalDate.now(Zone.KST.zoneId()));
    }

    // 이 페이지 회원들의 최근 30일(오늘 포함) 접속한 날 수를 일별 접속 기록에서 채운다. 한 번의 집계로 페이지 전체를 센다.
    private AdminMemberPageResponse withAccessDays(AdminMemberPageResponse page, LocalDate today) {
        if (page.items().isEmpty()) {
            return page;
        }
        Map<Long, Long> daysByUser = userAccessDailyRepository.countAccessDaysByUser(
                page.items().stream().map(AdminMemberResponse::id).toList(),
                today.minusDays(ACCESS_DAYS_WINDOW - 1L),
                today);
        List<AdminMemberResponse> items = page.items().stream()
                .map(member -> member.withAccessDays(daysByUser.getOrDefault(member.id(), 0L)))
                .toList();
        return new AdminMemberPageResponse(items, page.total(), page.accessTrackingAvailable());
    }

    /**
     * 체크한 회원을 특별 회원으로 지정하거나 해제한다. 일반 권한(USER)의 실제 가입 회원만 대상이고, 하나라도 대상이 아니면 아무도 바꾸지 않는다.
     */
    @Transactional
    public AdminMemberSpecialResponse changeSpecial(List<Long> userIds, boolean special) {
        List<UserAccount> users = findRegularMembers(userIds, "일반 회원만 특별 회원으로 바꿀 수 있습니다.");
        users.forEach(user -> user.changeSpecial(special));
        return new AdminMemberSpecialResponse(users.size());
    }

    /**
     * 체크한 회원의 닉네임을 지운다. 대상 조건은 특별 회원 지정과 같다. 닉네임이 없던 회원은 그대로 두고 센 수에서 뺀다.
     * 지워진 회원은 닉네임 변경 간격 없이 바로 새 닉네임을 등록할 수 있다.
     */
    @Transactional
    public AdminMemberNicknameResetResponse resetNicknames(List<Long> userIds) {
        List<UserAccount> users = findRegularMembers(userIds, "일반 회원의 닉네임만 지울 수 있습니다.");
        List<UserProfile> profiles = userProfileRepository.findAllById(
                users.stream().map(UserAccount::getId).toList());
        int reset = 0;
        for (UserProfile profile : profiles) {
            if (profile.getNickname() != null) {
                profile.resetNickname();
                reset++;
            }
        }
        return new AdminMemberNicknameResetResponse(reset);
    }

    private List<UserAccount> findRegularMembers(List<Long> userIds, String unsupportedMessage) {
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
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, unsupportedMessage);
        }
        return users;
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

    // 연동 필터는 실제 OAuth 발급자 주소와 미연동(NONE)을 쉼표로 여러 개 받는다. 넷 모두면 필터를 적용하지 않는다.
    private Set<String> parseIssuers(String issuer) {
        if (issuer.isEmpty()) {
            return Set.of();
        }
        Set<String> issuers = new HashSet<>();
        for (String token : Arrays.asList(issuer.split(",", -1))) {
            if (!MEMBER_ISSUER_FILTERS.contains(token)) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "회원 연동 조건이 올바르지 않습니다.");
            }
            issuers.add(token);
        }
        return issuers.size() == MEMBER_ISSUER_FILTERS.size() ? Set.of() : issuers;
    }

    /** 지금 시각을 받아 최근 가입·최근 접속 기준(각각 7일 전)을 정한다. */
    @Transactional(readOnly = true)
    public AdminMemberSummaryResponse getSummary(LocalDateTime now) {
        return userAccountRepository.summarize(now.minusDays(RECENT_JOIN_DAYS), now.minusDays(RECENT_ACCESS_DAYS));
    }

    /**
     * 오늘을 포함한 최근 days일의 일별 접속 회원 수. 접속한 사람이 없는 날도 0명으로 채워 날짜가 빠지지 않게 한다.
     * 접속 기록은 기록을 시작한 날부터만 있어서 그 전 날짜는 0명이다.
     */
    @Transactional(readOnly = true)
    public List<AdminDailyAccessResponse> getDailyAccess(LocalDate today, int days) {
        if (days < 1 || days > MAX_DAILY_ACCESS_DAYS) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "조회 기간이 올바르지 않습니다.");
        }
        LocalDate from = today.minusDays(days - 1L);
        Map<LocalDate, Long> countByDate = new HashMap<>();
        userAccessDailyRepository
                .countMembersByDate(from, today)
                .forEach(row -> countByDate.put(row.date(), row.members()));
        return from.datesUntil(today.plusDays(1))
                .map(date -> new AdminDailyAccessResponse(date, countByDate.getOrDefault(date, 0L)))
                .toList();
    }

    /**
     * 오늘을 포함한 최근 days일의 일별 신규 가입 수와 누적 회원 수. 가입한 사람이 없는 날도 0명으로 채운다.
     * 누적은 기간 시작 전에 이미 가입한 회원 수에서 출발해 하루씩 더한다(과거 가입일도 그대로 쓰므로 접속 기록과 달리 처음부터 채워진다).
     */
    @Transactional(readOnly = true)
    public List<AdminSignupDailyResponse> getSignups(LocalDate today, int days) {
        if (days < 1 || days > MAX_SIGNUP_DAYS) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "조회 기간이 올바르지 않습니다.");
        }
        LocalDate from = today.minusDays(days - 1L);
        Map<LocalDate, Long> joinedByDate = new HashMap<>();
        userAccountRepository
                .countJoinedByDate(from, today)
                .forEach(row -> joinedByDate.put(row.date(), row.members()));
        long[] total = {userAccountRepository.countJoinedBefore(from.atStartOfDay())};
        return from.datesUntil(today.plusDays(1))
                .map(date -> {
                    long joined = joinedByDate.getOrDefault(date, 0L);
                    total[0] += joined;
                    return new AdminSignupDailyResponse(date, joined, total[0]);
                })
                .toList();
    }

    /**
     * 최근 N일(오늘 포함)의 일별 비회원 방문자 수와 신규 가입 수, 가입 전환율(가입 수 ÷ 방문자 수). 방문자를 세기 시작하기 전의 날은
     * 방문자 0명으로 나오고 전환율은 비어 있다.
     */
    @Transactional(readOnly = true)
    public List<AdminVisitorDailyResponse> getVisitors(LocalDate today, int days) {
        if (days < 1 || days > MAX_SIGNUP_DAYS) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "조회 기간이 올바르지 않습니다.");
        }
        LocalDate from = today.minusDays(days - 1L);
        Map<LocalDate, Long> visitorsByDate = new HashMap<>();
        visitorDailyRepository
                .countVisitorsByDate(from, today)
                .forEach(row -> visitorsByDate.put(row.date(), row.members()));
        Map<LocalDate, Long> joinedByDate = new HashMap<>();
        userAccountRepository
                .countJoinedByDate(from, today)
                .forEach(row -> joinedByDate.put(row.date(), row.members()));
        return from.datesUntil(today.plusDays(1))
                .map(date -> {
                    long visitors = visitorsByDate.getOrDefault(date, 0L);
                    long joined = joinedByDate.getOrDefault(date, 0L);
                    Double rate = visitors == 0 ? null : joined * 100.0 / visitors;
                    return new AdminVisitorDailyResponse(date, visitors, joined, rate);
                })
                .toList();
    }

    /**
     * 최근 N일(오늘 포함)의 검색 로봇 방문. 이름별 합과 날짜별 합을 준다. 방문이 없는 날은 0이다.
     * 사람 방문 집계와는 별도다.
     */
    @Transactional(readOnly = true)
    public AdminBotVisitResponse getBotVisits(LocalDate today, int days) {
        if (days < 1 || days > MAX_SIGNUP_DAYS) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "조회 기간이 올바르지 않습니다.");
        }
        LocalDate from = today.minusDays(days - 1L);
        Map<LocalDate, Long> visitsByDate = new HashMap<>();
        visitorDailyRepository
                .countBotVisitsByDate(from, today)
                .forEach(row -> visitsByDate.put(row.date(), row.visits()));
        List<AdminBotDayCountResponse> byDay = from.datesUntil(today.plusDays(1))
                .map(date -> new AdminBotDayCountResponse(date, visitsByDate.getOrDefault(date, 0L)))
                .toList();
        return new AdminBotVisitResponse(visitorDailyRepository.countBotVisitsByName(from, today), byDay);
    }

    /**
     * 최근 N일(오늘 포함)의 비회원 방문을 유입 경로별로 센다. 네 경로가 모두 나오고 방문이 없는 경로는 0이다.
     * 출처를 기록하기 시작한 날부터만 세어진다.
     */
    @Transactional(readOnly = true)
    public List<AdminVisitorSourceResponse> getVisitorSources(LocalDate today, int days) {
        if (days < 1 || days > MAX_SIGNUP_DAYS) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "조회 기간이 올바르지 않습니다.");
        }
        List<AdminVisitorSourceResponse> counted =
                visitorDailyRepository.countVisitorsBySource(today.minusDays(days - 1L), today);
        return Arrays.stream(VisitorSource.values())
                .map(source -> counted.stream()
                        .filter(row -> row.source() == source)
                        .findFirst()
                        .orElse(new AdminVisitorSourceResponse(source, 0L)))
                .toList();
    }

    /**
     * 최근 30일(오늘 포함) 중 며칠 접속했는지로 회원을 구간에 나눠 센다. 한 번도 접속하지 않은 회원(기록이 생기기 전 가입자 포함)은
     * "0일"에 들어간다. 접속 기록은 기록을 시작한 날부터라 처음에는 "0일"이 많게 나온다.
     */
    @Transactional(readOnly = true)
    public List<AdminAccessFrequencyResponse> getAccessFrequency(LocalDate today) {
        List<Long> accessDays =
                userAccessDailyRepository.countAccessDaysPerMember(today.minusDays(ACCESS_DAYS_WINDOW - 1L), today);
        long neverAccessed = Math.max(0, userAccountRepository.countMembers() - accessDays.size());
        long[] counts = new long[ACCESS_FREQUENCY_LABELS.length];
        counts[0] = neverAccessed;
        for (long days : accessDays) {
            counts[accessFrequencyBucket(days)]++;
        }
        List<AdminAccessFrequencyResponse> result = new ArrayList<>();
        for (int i = 0; i < counts.length; i++) {
            result.add(new AdminAccessFrequencyResponse(ACCESS_FREQUENCY_LABELS[i], counts[i]));
        }
        return result;
    }

    // 1~2일, 3~6일, 7~14일, 15일 이상 — 0일은 접속 기록이 없는 회원이라 따로 센다.
    private static int accessFrequencyBucket(long days) {
        if (days <= 2) {
            return 1;
        }
        if (days <= 6) {
            return 2;
        }
        return days <= 14 ? 3 : 4;
    }
}
