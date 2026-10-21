package dev.eolmae.marketry.domain.auth.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import dev.eolmae.marketry.domain.auth.dto.AdminMemberPageResponse;
import dev.eolmae.marketry.domain.auth.dto.AdminMemberSpecialResponse;
import dev.eolmae.marketry.domain.auth.dto.AdminMemberSummaryResponse;
import dev.eolmae.marketry.domain.auth.entity.UserAccount;
import dev.eolmae.marketry.domain.auth.enums.MemberGrade;
import dev.eolmae.marketry.domain.auth.enums.Role;
import dev.eolmae.marketry.domain.auth.repository.UserAccountRepository;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.web.server.ResponseStatusException;

class AdminMemberServiceTest {
    private final UserAccountRepository repository = mock(UserAccountRepository.class);
    private final AdminMemberService service = new AdminMemberService(repository);

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
}
