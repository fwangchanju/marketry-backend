package kr.co.marketry.domain.auth.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import kr.co.marketry.common.event.UserSignedUpEvent;
import kr.co.marketry.domain.custom.entity.CustomValueTierThreshold;
import kr.co.marketry.domain.custom.entity.UserPreference;
import kr.co.marketry.domain.custom.repository.CustomValueTierThresholdRepository;
import kr.co.marketry.domain.custom.repository.UserPreferenceRepository;
import kr.co.marketry.domain.custom.service.ProtectedSectorService;
import kr.co.marketry.domain.custom.service.SuspendedStockClassificationService;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.jdbc.core.ConnectionCallback;
import org.springframework.jdbc.core.JdbcTemplate;

class SignupInitializationServiceTest {

    private static final long TEMPLATE_USER_ID = 1L;
    private static final long USER_ID = 41L;

    private final JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);
    private final CustomValueTierThresholdRepository customValueTierThresholdRepository =
            mock(CustomValueTierThresholdRepository.class);
    private final UserPreferenceRepository userPreferenceRepository = mock(UserPreferenceRepository.class);
    private final ProtectedSectorService protectedSectorService = mock(ProtectedSectorService.class);
    private final SuspendedStockClassificationService suspendedStockClassificationService =
            mock(SuspendedStockClassificationService.class);
    private final SignupInitializationService service = new SignupInitializationService(
            jdbcTemplate,
            customValueTierThresholdRepository,
            userPreferenceRepository,
            protectedSectorService,
            suspendedStockClassificationService);

    @Test
    void 가입시_보호_업종을_채우고_지금_거래_정지_종목을_거래_정지_업종에_맞춘다() {
        service.onUserSignedUp(new UserSignedUpEvent(USER_ID));

        verify(protectedSectorService).ensureForUser(USER_ID);
        verify(suspendedStockClassificationService).applyCurrentExclusionsToUser(USER_ID);
    }

    @Test
    void 가입시_advisory_lock을_잡는다() {
        service.onUserSignedUp(new UserSignedUpEvent(USER_ID));

        verify(jdbcTemplate).execute(any(ConnectionCallback.class));
    }

    @Test
    void 시가총액_구간만_복제하고_설정은_복제하지_않는다() {
        when(customValueTierThresholdRepository.findAllByUserIdOrderByThresholdValueAsc(TEMPLATE_USER_ID))
                .thenReturn(List.of(CustomValueTierThreshold.create(TEMPLATE_USER_ID, "소형주", 0L, true)));
        UserPreference templatePreference = UserPreference.createEmpty(TEMPLATE_USER_ID);
        templatePreference.overwrite("{\"theme\":\"dark\"}");
        when(userPreferenceRepository.findById(TEMPLATE_USER_ID)).thenReturn(Optional.of(templatePreference));

        service.onUserSignedUp(new UserSignedUpEvent(USER_ID));

        assertThat(captureSaveAll(customValueTierThresholdRepository))
                .extracting(
                        CustomValueTierThreshold::getUserId,
                        CustomValueTierThreshold::getLabel,
                        CustomValueTierThreshold::getThresholdValue,
                        CustomValueTierThreshold::isExcludedByDefault)
                .containsExactly(tuple(USER_ID, "소형주", 0L, true));
        assertThat(savedPreference())
                .extracting(UserPreference::getUserId, UserPreference::getPayload)
                .containsExactly(USER_ID, "{}");
    }

    @Test
    void 템플릿에_구간이_없으면_아무것도_복제하지_않고_빈_설정을_만든다() {
        service.onUserSignedUp(new UserSignedUpEvent(USER_ID));

        assertThat(captureSaveAll(customValueTierThresholdRepository)).isEmpty();
        assertThat(savedPreference())
                .extracting(UserPreference::getUserId, UserPreference::getPayload)
                .containsExactly(USER_ID, "{}");
    }

    @SuppressWarnings("unchecked")
    private static <T> List<T> captureSaveAll(org.springframework.data.repository.CrudRepository<T, ?> repository) {
        ArgumentCaptor<Iterable<T>> captor = ArgumentCaptor.forClass(Iterable.class);
        verify(repository).saveAll(captor.capture());
        List<T> saved = new ArrayList<>();
        captor.getValue().forEach(saved::add);
        return saved;
    }

    private UserPreference savedPreference() {
        ArgumentCaptor<UserPreference> captor = ArgumentCaptor.forClass(UserPreference.class);
        verify(userPreferenceRepository).save(captor.capture());
        return captor.getValue();
    }
}
