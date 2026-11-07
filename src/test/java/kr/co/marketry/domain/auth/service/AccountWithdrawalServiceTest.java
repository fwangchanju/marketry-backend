package kr.co.marketry.domain.auth.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Optional;
import kr.co.marketry.domain.auth.entity.UserAccount;
import kr.co.marketry.domain.auth.enums.Role;
import kr.co.marketry.domain.auth.repository.UserAccessDailyRepository;
import kr.co.marketry.domain.auth.repository.UserAccountRepository;
import kr.co.marketry.domain.auth.repository.UserProfileRepository;
import kr.co.marketry.domain.auth.repository.UserRefreshTokenRepository;
import kr.co.marketry.domain.custom.repository.CustomScaleThresholdRepository;
import kr.co.marketry.domain.custom.repository.CustomSectorRepository;
import kr.co.marketry.domain.custom.repository.CustomSnapshotRepository;
import kr.co.marketry.domain.custom.repository.CustomStockAliasRepository;
import kr.co.marketry.domain.custom.repository.CustomStockSectorRepository;
import kr.co.marketry.domain.custom.repository.CustomValueTierThresholdRepository;
import kr.co.marketry.domain.custom.repository.UserPreferenceRepository;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

class AccountWithdrawalServiceTest {
    private static final long USER_ID = 42L;

    private final UserAccountRepository userAccountRepository = mock(UserAccountRepository.class);
    private final UserProfileRepository userProfileRepository = mock(UserProfileRepository.class);
    private final UserRefreshTokenRepository userRefreshTokenRepository = mock(UserRefreshTokenRepository.class);
    private final UserAccessDailyRepository userAccessDailyRepository = mock(UserAccessDailyRepository.class);
    private final UserPreferenceRepository userPreferenceRepository = mock(UserPreferenceRepository.class);
    private final CustomStockSectorRepository customStockSectorRepository = mock(CustomStockSectorRepository.class);
    private final CustomStockAliasRepository customStockAliasRepository = mock(CustomStockAliasRepository.class);
    private final CustomSectorRepository customSectorRepository = mock(CustomSectorRepository.class);
    private final CustomSnapshotRepository customSnapshotRepository = mock(CustomSnapshotRepository.class);
    private final CustomScaleThresholdRepository customScaleThresholdRepository =
            mock(CustomScaleThresholdRepository.class);
    private final CustomValueTierThresholdRepository customValueTierThresholdRepository =
            mock(CustomValueTierThresholdRepository.class);
    private final AccountWithdrawalService service = new AccountWithdrawalService(
            userAccountRepository,
            userProfileRepository,
            userAccessDailyRepository,
            userRefreshTokenRepository,
            userPreferenceRepository,
            customStockSectorRepository,
            customStockAliasRepository,
            customSectorRepository,
            customSnapshotRepository,
            customScaleThresholdRepository,
            customValueTierThresholdRepository);

    @Test
    void 탈퇴하면_종목_배정_업종_스냅샷_순서로_지우고_회원을_마지막에_지운다() {
        UserAccount user = user(Role.USER, "https://accounts.google.com");
        when(userAccountRepository.findById(USER_ID)).thenReturn(Optional.of(user));

        service.withdraw(USER_ID);

        var order = inOrder(
                customStockSectorRepository,
                customSectorRepository,
                customSnapshotRepository,
                userProfileRepository,
                userAccountRepository);
        order.verify(customStockSectorRepository).deleteAllByUserId(USER_ID);
        order.verify(customSectorRepository).deleteAllOfUser(USER_ID);
        order.verify(customSnapshotRepository).deleteAllOfUser(USER_ID);
        order.verify(userProfileRepository).deleteAllOfUser(USER_ID);
        order.verify(userAccountRepository).deleteById(USER_ID);
        verify(customStockAliasRepository).deleteAllOfUser(USER_ID);
        verify(customScaleThresholdRepository).deleteAllOfUser(USER_ID);
        verify(customValueTierThresholdRepository).deleteAllByUserId(USER_ID);
        verify(userPreferenceRepository).deleteAllOfUser(USER_ID);
        verify(userRefreshTokenRepository).deleteAllOfUser(USER_ID);
        verify(userAccessDailyRepository).deleteAllOfUser(USER_ID);
    }

    @Test
    void 관리자는_탈퇴할_수_없고_아무것도_지우지_않는다() {
        UserAccount user = user(Role.ADMIN, "https://accounts.google.com");
        when(userAccountRepository.findById(USER_ID)).thenReturn(Optional.of(user));

        assertThatThrownBy(() -> service.withdraw(USER_ID))
                .isInstanceOfSatisfying(
                        ResponseStatusException.class,
                        exception -> assertThat(exception.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN));
        verify(userAccountRepository, never()).deleteById(any());
        verify(customSectorRepository, never()).deleteAllOfUser(any());
    }

    @Test
    void 시스템_회원은_탈퇴할_수_없다() {
        UserAccount user = user(Role.USER, UserAccount.SYSTEM_ISSUER);
        when(userAccountRepository.findById(USER_ID)).thenReturn(Optional.of(user));

        assertThatThrownBy(() -> service.withdraw(USER_ID)).isInstanceOf(ResponseStatusException.class);
        verify(userAccountRepository, never()).deleteById(any());
    }

    @Test
    void 없는_회원이면_404를_던진다() {
        when(userAccountRepository.findById(USER_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.withdraw(USER_ID))
                .isInstanceOfSatisfying(
                        ResponseStatusException.class,
                        exception -> assertThat(exception.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND));
    }

    private UserAccount user(Role role, String issuer) {
        UserAccount user = mock(UserAccount.class);
        when(user.getRole()).thenReturn(role);
        when(user.getIssuer()).thenReturn(issuer);
        return user;
    }
}
