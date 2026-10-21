package dev.eolmae.marketry.domain.auth.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import dev.eolmae.marketry.domain.auth.dto.AdminMemberPageResponse;
import dev.eolmae.marketry.domain.auth.enums.Role;
import dev.eolmae.marketry.domain.auth.repository.UserAccountRepository;
import java.util.List;
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
        when(repository.findMembers("회원", Role.USER, "createdAt", false, 2, 50)).thenReturn(expected);
        assertThat(service.findMembers(" 회원 ", "USER", "createdAt", "desc", 2)).isEqualTo(expected);
    }

    @Test
    void 전체_권한은_필터_없이_조회한다() {
        var expected = new AdminMemberPageResponse(List.of(), 0, false);
        when(repository.findMembers("", null, "createdAt", false, 0, 50)).thenReturn(expected);
        assertThat(service.findMembers("", "", "createdAt", "desc", 0)).isEqualTo(expected);
    }

    @Test
    void 관리자_권한으로_조회한다() {
        var expected = new AdminMemberPageResponse(List.of(), 0, false);
        when(repository.findMembers("", Role.ADMIN, "createdAt", false, 0, 50)).thenReturn(expected);
        assertThat(service.findMembers("", "ADMIN", "createdAt", "desc", 0)).isEqualTo(expected);
    }

    @ParameterizedTest
    @ValueSource(strings = {"id", "email", "nickname", "role", "createdAt"})
    void 지원하는_정렬_기준은_방향과_함께_저장소에_넘긴다(String sort) {
        var ascending = new AdminMemberPageResponse(List.of(), 1, false);
        var descending = new AdminMemberPageResponse(List.of(), 2, false);
        when(repository.findMembers("", null, sort, true, 0, 50)).thenReturn(ascending);
        when(repository.findMembers("", null, sort, false, 0, 50)).thenReturn(descending);
        assertThat(service.findMembers("", "", sort, "asc", 0)).isEqualTo(ascending);
        assertThat(service.findMembers("", "", sort, "desc", 0)).isEqualTo(descending);
    }

    @ParameterizedTest
    @ValueSource(strings = {"lastAccessAt", "invalid", ""})
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
}
