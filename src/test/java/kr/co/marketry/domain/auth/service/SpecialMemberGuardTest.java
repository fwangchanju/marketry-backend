package kr.co.marketry.domain.auth.service;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.util.Optional;
import kr.co.marketry.domain.auth.entity.UserAccount;
import kr.co.marketry.domain.auth.enums.Role;
import kr.co.marketry.domain.auth.repository.UserAccountRepository;
import org.junit.jupiter.api.Test;
import org.springframework.web.server.ResponseStatusException;

class SpecialMemberGuardTest {
    private final UserAccountRepository users = mock(UserAccountRepository.class);
    private final SpecialMemberGuard guard = new SpecialMemberGuard(users);

    private void memberIs(long userId, boolean special) {
        UserAccount user = mock(UserAccount.class);
        when(user.isSpecial()).thenReturn(special);
        when(users.findById(userId)).thenReturn(Optional.of(user));
    }

    @Test
    void 관리자는_회원_정보를_읽지_않고_통과한다() {
        assertThatCode(() -> guard.require(new AuthenticatedUserPrincipal(2L, Role.ADMIN)))
                .doesNotThrowAnyException();
        verifyNoInteractions(users);
    }

    @Test
    void 특별_회원은_통과한다() {
        memberIs(1L, true);

        assertThatCode(() -> guard.require(new AuthenticatedUserPrincipal(1L, Role.USER)))
                .doesNotThrowAnyException();
    }

    @Test
    void 일반_회원은_막는다() {
        memberIs(1L, false);

        assertThatThrownBy(() -> guard.require(new AuthenticatedUserPrincipal(1L, Role.USER)))
                .isInstanceOf(ResponseStatusException.class);
    }

    @Test
    void 없는_회원은_막는다() {
        when(users.findById(1L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> guard.require(new AuthenticatedUserPrincipal(1L, Role.USER)))
                .isInstanceOf(ResponseStatusException.class);
    }

    @Test
    void 읽기_전용_캡처_토큰은_회원이_특별_회원이_아니면_막는다() {
        memberIs(3L, false);

        assertThatThrownBy(() -> guard.require(new AuthenticatedUserPrincipal(3L, Role.USER, true)))
                .isInstanceOf(ResponseStatusException.class);
    }
}
