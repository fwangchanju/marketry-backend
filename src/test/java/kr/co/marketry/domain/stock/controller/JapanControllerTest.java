package kr.co.marketry.domain.stock.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.util.Map;
import java.util.Optional;
import kr.co.marketry.domain.auth.entity.UserAccount;
import kr.co.marketry.domain.auth.enums.Role;
import kr.co.marketry.domain.auth.repository.UserAccountRepository;
import kr.co.marketry.domain.auth.service.AuthenticatedUserPrincipal;
import kr.co.marketry.domain.stock.service.JapanStockKoreanNameService;
import kr.co.marketry.domain.stock.service.OverseasClassificationCategoryService;
import kr.co.marketry.domain.stock.service.OverseasStockClassificationService;
import kr.co.marketry.domain.stock.service.OverseasStockQuoteService;
import org.junit.jupiter.api.Test;
import org.springframework.web.server.ResponseStatusException;

class JapanControllerTest {
    private final UserAccountRepository users = mock(UserAccountRepository.class);
    private final JapanStockKoreanNameService names = mock(JapanStockKoreanNameService.class);
    private final JapanController controller = new JapanController(
            users,
            mock(OverseasStockQuoteService.class),
            mock(OverseasStockClassificationService.class),
            mock(OverseasClassificationCategoryService.class),
            names);

    private void memberIs(boolean special) {
        UserAccount user = mock(UserAccount.class);
        when(user.isSpecial()).thenReturn(special);
        when(users.findById(1L)).thenReturn(Optional.of(user));
    }

    @Test
    void 일반_회원은_막는다() {
        memberIs(false);

        assertThatThrownBy(() -> controller.findStockNames(new AuthenticatedUserPrincipal(1L, Role.USER)))
                .isInstanceOf(ResponseStatusException.class);
        verifyNoInteractions(names);
    }

    @Test
    void 없는_회원은_막는다() {
        when(users.findById(1L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> controller.findStockNames(new AuthenticatedUserPrincipal(1L, Role.USER)))
                .isInstanceOf(ResponseStatusException.class);
    }

    @Test
    void 특별_회원은_조회한다() {
        memberIs(true);
        when(names.findAll()).thenReturn(Map.of("7203", "도요타"));

        assertThat(controller.findStockNames(new AuthenticatedUserPrincipal(1L, Role.USER)))
                .containsEntry("7203", "도요타");
    }

    @Test
    void 관리자는_회원_정보를_읽지_않고_조회한다() {
        when(names.findAll()).thenReturn(Map.of());

        assertThat(controller.findStockNames(new AuthenticatedUserPrincipal(2L, Role.ADMIN)))
                .isEmpty();
        verifyNoInteractions(users);
    }
}
