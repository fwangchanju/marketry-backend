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
        when(repository.findMembers("회원", Role.USER, 2, 50)).thenReturn(expected);
        assertThat(service.findMembers(" 회원 ", "USER", "createdAt", 2)).isEqualTo(expected);
    }

    @Test
    void 전체_권한은_필터_없이_조회한다() {
        var expected = new AdminMemberPageResponse(List.of(), 0, false);
        when(repository.findMembers("", null, 0, 50)).thenReturn(expected);
        assertThat(service.findMembers("", "", "createdAt", 0)).isEqualTo(expected);
    }

    @Test
    void 관리자_권한으로_조회한다() {
        var expected = new AdminMemberPageResponse(List.of(), 0, false);
        when(repository.findMembers("", Role.ADMIN, 0, 50)).thenReturn(expected);
        assertThat(service.findMembers("", "ADMIN", "createdAt", 0)).isEqualTo(expected);
    }

    @ParameterizedTest
    @ValueSource(strings = {"lastAccessAt", "email", "invalid"})
    void 지원하지_않는_정렬은_거부한다(String sort) {
        assertThatThrownBy(() -> service.findMembers("", "", sort, 0)).isInstanceOf(ResponseStatusException.class);
        verifyNoInteractions(repository);
    }

    @Test
    void 잘못된_페이지_검색어_권한은_조회하지_않는다() {
        assertThatThrownBy(() -> service.findMembers("", "", "createdAt", -1))
                .isInstanceOf(ResponseStatusException.class);
        assertThatThrownBy(() -> service.findMembers("a".repeat(101), "", "createdAt", 0))
                .isInstanceOf(ResponseStatusException.class);
        assertThatThrownBy(() -> service.findMembers("", "OWNER", "createdAt", 0))
                .isInstanceOf(ResponseStatusException.class);
        verifyNoInteractions(repository);
    }
}
