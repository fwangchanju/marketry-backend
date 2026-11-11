package kr.co.marketry.domain.auth.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Set;
import kr.co.marketry.domain.auth.dto.AdminAccessFrequencyResponse;
import kr.co.marketry.domain.auth.dto.AdminBotDayCountResponse;
import kr.co.marketry.domain.auth.dto.AdminBotNameCountResponse;
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
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.web.server.ResponseStatusException;

class AdminMemberServiceTest {
    private final UserAccountRepository repository = mock(UserAccountRepository.class);
    private final UserAccessDailyRepository accessDailyRepository = mock(UserAccessDailyRepository.class);
    private final UserProfileRepository profileRepository = mock(UserProfileRepository.class);
    private final VisitorDailyRepository visitorRepository = mock(VisitorDailyRepository.class);
    private final AdminMemberService service =
            new AdminMemberService(repository, accessDailyRepository, profileRepository, visitorRepository);

    @Test
    void 검색_로봇_방문은_이름별과_날짜별로_나오고_방문이_없는_날은_0이다() {
        LocalDate today = LocalDate.of(2026, 11, 11);
        when(visitorRepository.countBotVisitsByDate(today.minusDays(2), today))
                .thenReturn(List.of(
                        new AdminBotDayCountResponse(today.minusDays(2), 5L), new AdminBotDayCountResponse(today, 2L)));
        when(visitorRepository.countBotVisitsByName(today.minusDays(2), today))
                .thenReturn(List.of(
                        new AdminBotNameCountResponse("yeti", 6L), new AdminBotNameCountResponse("daumoa", 1L)));

        var result = service.getBotVisits(today, 3);

        assertThat(result.byDay())
                .containsExactly(
                        new AdminBotDayCountResponse(today.minusDays(2), 5L),
                        new AdminBotDayCountResponse(today.minusDays(1), 0L),
                        new AdminBotDayCountResponse(today, 2L));
        assertThat(result.byName()).extracting(AdminBotNameCountResponse::name).containsExactly("yeti", "daumoa");
        assertThatThrownBy(() -> service.getBotVisits(today, 0)).isInstanceOf(ResponseStatusException.class);
    }

    @Test
    void 검색어_공백을_제거하고_권한과_50명_페이지를_조회한다() {
        var expected = new AdminMemberPageResponse(List.of(), 0, false);
        when(repository.findMembers("회원", Set.of(MemberGrade.USER), "createdAt", false, 2, 50))
                .thenReturn(expected);
        assertThat(service.findMembers(" 회원 ", "USER", "createdAt", "desc", 2)).isEqualTo(expected);
    }

    @Test
    void 전체_권한은_필터_없이_조회한다() {
        var expected = new AdminMemberPageResponse(List.of(), 0, false);
        when(repository.findMembers("", Set.of(), "createdAt", false, 0, 50)).thenReturn(expected);
        assertThat(service.findMembers("", "", "createdAt", "desc", 0)).isEqualTo(expected);
    }

    @Test
    void 관리자_권한으로_조회한다() {
        var expected = new AdminMemberPageResponse(List.of(), 0, false);
        when(repository.findMembers("", Set.of(MemberGrade.ADMIN), "createdAt", false, 0, 50))
                .thenReturn(expected);
        assertThat(service.findMembers("", "ADMIN", "createdAt", "desc", 0)).isEqualTo(expected);
    }

    @ParameterizedTest
    @ValueSource(strings = {"id", "issuer", "email", "nickname", "role", "createdAt", "lastAccessAt"})
    void 지원하는_정렬_기준은_방향과_함께_저장소에_넘긴다(String sort) {
        var ascending = new AdminMemberPageResponse(List.of(), 1, false);
        var descending = new AdminMemberPageResponse(List.of(), 2, false);
        when(repository.findMembers("", Set.of(), sort, true, 0, 50)).thenReturn(ascending);
        when(repository.findMembers("", Set.of(), sort, false, 0, 50)).thenReturn(descending);
        assertThat(service.findMembers("", "", sort, "asc", 0)).isEqualTo(ascending);
        assertThat(service.findMembers("", "", sort, "desc", 0)).isEqualTo(descending);
    }

    @ParameterizedTest
    @ValueSource(strings = {"invalid", "password", ""})
    void 지원하지_않는_정렬은_거부한다(String sort) {
        assertThatThrownBy(() -> service.findMembers("", "", sort, "desc", 0))
                .isInstanceOf(ResponseStatusException.class);
        verifyNoInteractions(repository);
    }

    @ParameterizedTest
    @ValueSource(strings = {"up", "ASC", ""})
    void 지원하지_않는_정렬_방향은_거부한다(String order) {
        assertThatThrownBy(() -> service.findMembers("", "", "createdAt", order, 0))
                .isInstanceOf(ResponseStatusException.class);
        verifyNoInteractions(repository);
    }

    @Test
    void 잘못된_페이지_검색어_권한은_조회하지_않는다() {
        assertThatThrownBy(() -> service.findMembers("", "", "createdAt", "desc", -1))
                .isInstanceOf(ResponseStatusException.class);
        assertThatThrownBy(() -> service.findMembers("a".repeat(101), "", "createdAt", "desc", 0))
                .isInstanceOf(ResponseStatusException.class);
        assertThatThrownBy(() -> service.findMembers("", "OWNER", "createdAt", "desc", 0))
                .isInstanceOf(ResponseStatusException.class);
        verifyNoInteractions(repository);
    }

    @Test
    void 현황은_지금부터_7일_전을_최근_가입과_최근_접속_기준으로_조회한다() {
        var expected = new AdminMemberSummaryResponse(6, 1, 2, 3, 2, 4);
        when(repository.summarize(LocalDateTime.of(2026, 10, 14, 12, 0), LocalDateTime.of(2026, 10, 14, 12, 0)))
                .thenReturn(expected);
        assertThat(service.getSummary(LocalDateTime.of(2026, 10, 21, 12, 0))).isEqualTo(expected);
    }

    @Test
    void 쉼표로_구분한_여러_회원_구분으로_조회한다() {
        var expected = new AdminMemberPageResponse(List.of(), 3, false);
        when(repository.findMembers("", Set.of(MemberGrade.USER, MemberGrade.SPECIAL), "createdAt", false, 0, 50))
                .thenReturn(expected);
        assertThat(service.findMembers("", "USER,SPECIAL", "createdAt", "desc", 0))
                .isEqualTo(expected);
    }

    @Test
    void 특별_회원만_조회한다() {
        var expected = new AdminMemberPageResponse(List.of(), 2, false);
        when(repository.findMembers("", Set.of(MemberGrade.SPECIAL), "createdAt", false, 0, 50))
                .thenReturn(expected);
        assertThat(service.findMembers("", "SPECIAL", "createdAt", "desc", 0)).isEqualTo(expected);
    }

    @Test
    void 세_구분을_모두_고르면_구분_없이_조회한다() {
        var expected = new AdminMemberPageResponse(List.of(), 6, false);
        when(repository.findMembers("", Set.of(), "createdAt", false, 0, 50)).thenReturn(expected);
        assertThat(service.findMembers("", "USER,SPECIAL,ADMIN", "createdAt", "desc", 0))
                .isEqualTo(expected);
    }

    @ParameterizedTest
    @ValueSource(strings = {"user", "OWNER", "USER,", ",ADMIN", "USER,OWNER"})
    void 올바르지_않은_회원_구분은_거부한다(String role) {
        assertThatThrownBy(() -> service.findMembers("", role, "createdAt", "desc", 0))
                .isInstanceOf(ResponseStatusException.class);
        verifyNoInteractions(repository);
    }

    private UserAccount member(Role role, String issuer) {
        UserAccount user = mock(UserAccount.class);
        when(user.getRole()).thenReturn(role);
        when(user.getIssuer()).thenReturn(issuer);
        return user;
    }

    @Test
    void 일반_권한_가입_회원은_특별_회원으로_지정한다() {
        UserAccount first = member(Role.USER, "https://accounts.google.com");
        UserAccount second = member(Role.USER, "https://accounts.google.com");
        when(repository.findAllById(Set.of(2L, 3L))).thenReturn(List.of(first, second));

        AdminMemberSpecialResponse response = service.changeSpecial(List.of(2L, 3L, 3L), true);

        assertThat(response.changed()).isEqualTo(2);
        verify(first).changeSpecial(true);
        verify(second).changeSpecial(true);
    }

    @Test
    void 특별_회원을_일반으로_해제한다() {
        UserAccount user = member(Role.USER, "https://accounts.google.com");
        when(repository.findAllById(Set.of(2L))).thenReturn(List.of(user));

        service.changeSpecial(List.of(2L), false);

        verify(user).changeSpecial(false);
    }

    @Test
    void 관리자_시스템_테스트_회원이_하나라도_섞이면_아무도_바꾸지_않는다() {
        UserAccount normal = member(Role.USER, "https://accounts.google.com");
        for (UserAccount unsupported : List.of(
                member(Role.ADMIN, "https://accounts.google.com"),
                member(Role.USER, UserAccount.SYSTEM_ISSUER),
                member(Role.USER, UserAccount.LOCAL_TEST_ISSUER))) {
            when(repository.findAllById(Set.of(2L, 3L))).thenReturn(List.of(normal, unsupported));

            assertThatThrownBy(() -> service.changeSpecial(List.of(2L, 3L), true))
                    .isInstanceOf(ResponseStatusException.class);
        }
        verify(normal, never()).changeSpecial(true);
    }

    @Test
    void 없는_회원이_섞이면_바꾸지_않는다() {
        UserAccount user = member(Role.USER, "https://accounts.google.com");
        when(repository.findAllById(Set.of(2L, 99L))).thenReturn(List.of(user));

        assertThatThrownBy(() -> service.changeSpecial(List.of(2L, 99L), true))
                .isInstanceOf(ResponseStatusException.class);
        verify(user, never()).changeSpecial(true);
    }

    @Test
    void 선택이_비었거나_너무_많으면_거부한다() {
        assertThatThrownBy(() -> service.changeSpecial(List.of(), true)).isInstanceOf(ResponseStatusException.class);
        assertThatThrownBy(() -> service.changeSpecial(null, true)).isInstanceOf(ResponseStatusException.class);
        List<Long> tooMany =
                java.util.stream.LongStream.rangeClosed(1, 101).boxed().toList();
        assertThatThrownBy(() -> service.changeSpecial(tooMany, true)).isInstanceOf(ResponseStatusException.class);
    }

    @Test
    void 일별_접속은_기간의_모든_날짜를_채우고_접속한_사람이_없는_날은_0명이다() {
        LocalDate today = LocalDate.of(2026, 10, 21);
        when(accessDailyRepository.countMembersByDate(LocalDate.of(2026, 10, 19), today))
                .thenReturn(List.of(
                        new AdminDailyAccessResponse(LocalDate.of(2026, 10, 19), 2),
                        new AdminDailyAccessResponse(today, 5)));

        assertThat(service.getDailyAccess(today, 3))
                .containsExactly(
                        new AdminDailyAccessResponse(LocalDate.of(2026, 10, 19), 2),
                        new AdminDailyAccessResponse(LocalDate.of(2026, 10, 20), 0),
                        new AdminDailyAccessResponse(today, 5));
    }

    @ParameterizedTest
    @ValueSource(ints = {0, -1, 91})
    void 일별_접속은_1일_미만_또는_90일_초과_기간을_거부한다(int days) {
        assertThatThrownBy(() -> service.getDailyAccess(LocalDate.of(2026, 10, 21), days))
                .isInstanceOf(ResponseStatusException.class);
        verifyNoInteractions(accessDailyRepository);
    }

    @Test
    void 회원_목록은_이_페이지_회원들의_최근_30일_접속한_날_수를_채운다() {
        var active = new AdminMemberResponse(
                1L, "issuer", "a@example.com", "가", Role.USER, false, LocalDateTime.of(2026, 9, 1, 9, 0), null, null);
        var inactive = new AdminMemberResponse(
                2L, "issuer", "b@example.com", "나", Role.USER, false, LocalDateTime.of(2026, 9, 2, 9, 0), null, null);
        when(repository.findMembers("", Set.of(), "createdAt", false, 0, 50))
                .thenReturn(new AdminMemberPageResponse(List.of(active, inactive), 2, true));
        when(accessDailyRepository.countAccessDaysByUser(
                        eq(List.of(1L, 2L)), any(LocalDate.class), any(LocalDate.class)))
                .thenReturn(Map.of(1L, 12L));

        var result = service.findMembers("", "", "createdAt", "desc", 0);

        assertThat(result.items()).extracting(AdminMemberResponse::accessDays).containsExactly(12L, 0L);
        assertThat(result.total()).isEqualTo(2);
        assertThat(result.accessTrackingAvailable()).isTrue();
    }

    @Test
    void 가입_추이는_기간_시작_전_회원_수에서_출발해_하루씩_누적한다() {
        LocalDate today = LocalDate.of(2026, 10, 21);
        LocalDate from = LocalDate.of(2026, 10, 19);
        when(repository.countJoinedByDate(from, today))
                .thenReturn(List.of(new AdminDailyAccessResponse(from, 2), new AdminDailyAccessResponse(today, 1)));
        when(repository.countJoinedBefore(from.atStartOfDay())).thenReturn(10L);

        assertThat(service.getSignups(today, 3))
                .containsExactly(
                        new AdminSignupDailyResponse(from, 2, 12),
                        new AdminSignupDailyResponse(LocalDate.of(2026, 10, 20), 0, 12),
                        new AdminSignupDailyResponse(today, 1, 13));
    }

    @ParameterizedTest
    @ValueSource(ints = {0, -1, 181})
    void 가입_추이는_1일_미만_또는_180일_초과_기간을_거부한다(int days) {
        assertThatThrownBy(() -> service.getSignups(LocalDate.of(2026, 10, 21), days))
                .isInstanceOf(ResponseStatusException.class);
        verifyNoInteractions(repository);
    }

    @Test
    void 접속_빈도는_접속한_날_수를_구간으로_나누고_한_번도_접속하지_않은_회원은_0일로_센다() {
        LocalDate today = LocalDate.of(2026, 10, 21);
        // 접속한 회원 6명: 1일, 2일, 3일, 6일, 7일, 14일 — 마지막에 15일, 30일도 있어 8명
        when(accessDailyRepository.countAccessDaysPerMember(today.minusDays(29), today))
                .thenReturn(List.of(1L, 2L, 3L, 6L, 7L, 14L, 15L, 30L));
        when(repository.countNonAdminMembers()).thenReturn(11L);

        assertThat(service.getAccessFrequency(today))
                .containsExactly(
                        new AdminAccessFrequencyResponse("0일", 3),
                        new AdminAccessFrequencyResponse("1~2일", 2),
                        new AdminAccessFrequencyResponse("3~6일", 2),
                        new AdminAccessFrequencyResponse("7~14일", 2),
                        new AdminAccessFrequencyResponse("15일 이상", 2));
    }

    @Test
    void 접속_빈도는_전체_회원_수가_접속_회원_수보다_적어도_음수로_세지_않는다() {
        LocalDate today = LocalDate.of(2026, 10, 21);
        when(accessDailyRepository.countAccessDaysPerMember(today.minusDays(29), today))
                .thenReturn(List.of(1L, 1L));
        when(repository.countNonAdminMembers()).thenReturn(1L);

        assertThat(service.getAccessFrequency(today).get(0)).isEqualTo(new AdminAccessFrequencyResponse("0일", 0));
    }

    @Test
    void 닉네임이_있는_회원만_닉네임을_지우고_센다() {
        UserAccount first = member(Role.USER, "https://accounts.google.com");
        UserAccount second = member(Role.USER, "https://accounts.google.com");
        when(first.getId()).thenReturn(2L);
        when(second.getId()).thenReturn(3L);
        when(repository.findAllById(Set.of(2L, 3L))).thenReturn(List.of(first, second));
        UserProfile withNickname = mock(UserProfile.class);
        when(withNickname.getNickname()).thenReturn("나쁜말");
        UserProfile withoutNickname = mock(UserProfile.class);
        when(profileRepository.findAllById(List.of(2L, 3L))).thenReturn(List.of(withNickname, withoutNickname));

        AdminMemberNicknameResetResponse response = service.resetNicknames(List.of(2L, 3L));

        assertThat(response.reset()).isEqualTo(1);
        verify(withNickname).resetNickname();
        verify(withoutNickname, never()).resetNickname();
    }

    @Test
    void 관리자나_시스템_회원이_섞이면_닉네임을_지우지_않는다() {
        UserAccount unsupported = member(Role.ADMIN, "https://accounts.google.com");
        when(repository.findAllById(Set.of(1L))).thenReturn(List.of(unsupported));

        assertThatThrownBy(() -> service.resetNicknames(List.of(1L))).isInstanceOf(ResponseStatusException.class);
        verifyNoInteractions(profileRepository);
    }

    @Test
    void 방문자_수와_가입_수로_일별_전환율을_계산한다() {
        LocalDate today = LocalDate.of(2026, 10, 21);
        LocalDate from = LocalDate.of(2026, 10, 19);
        when(visitorRepository.countVisitorsByDate(from, today))
                .thenReturn(List.of(new AdminDailyAccessResponse(from, 40), new AdminDailyAccessResponse(today, 10)));
        when(repository.countJoinedByDate(from, today)).thenReturn(List.of(new AdminDailyAccessResponse(today, 1)));

        assertThat(service.getVisitors(today, 3))
                .containsExactly(
                        new AdminVisitorDailyResponse(from, 40, 0, 0.0),
                        new AdminVisitorDailyResponse(LocalDate.of(2026, 10, 20), 0, 0, null),
                        new AdminVisitorDailyResponse(today, 10, 1, 10.0));
    }

    @Test
    void 방문자_조회_기간이_범위를_벗어나면_거부한다() {
        LocalDate today = LocalDate.of(2026, 10, 21);
        assertThatThrownBy(() -> service.getVisitors(today, 0)).isInstanceOf(ResponseStatusException.class);
        assertThatThrownBy(() -> service.getVisitors(today, 181)).isInstanceOf(ResponseStatusException.class);
    }

    @Test
    void 유입_경로는_네_경로를_모두_돌려주고_방문이_없는_경로는_0이다() {
        LocalDate today = LocalDate.of(2026, 10, 24);
        when(visitorRepository.countVisitorsBySource(today.minusDays(29), today))
                .thenReturn(List.of(
                        new AdminVisitorSourceResponse(VisitorSource.NAVER, 3),
                        new AdminVisitorSourceResponse(VisitorSource.DIRECT, 5)));

        assertThat(service.getVisitorSources(today, 30))
                .containsExactly(
                        new AdminVisitorSourceResponse(VisitorSource.NAVER, 3),
                        new AdminVisitorSourceResponse(VisitorSource.GOOGLE, 0),
                        new AdminVisitorSourceResponse(VisitorSource.DIRECT, 5),
                        new AdminVisitorSourceResponse(VisitorSource.OTHER, 0));
    }

    @Test
    void 유입_경로_조회_기간이_범위를_벗어나면_거부한다() {
        LocalDate today = LocalDate.of(2026, 10, 24);
        assertThatThrownBy(() -> service.getVisitorSources(today, 0)).isInstanceOf(ResponseStatusException.class);
        assertThatThrownBy(() -> service.getVisitorSources(today, 181)).isInstanceOf(ResponseStatusException.class);
    }
}
