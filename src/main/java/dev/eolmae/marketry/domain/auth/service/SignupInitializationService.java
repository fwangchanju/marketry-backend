package dev.eolmae.marketry.domain.auth.service;

import dev.eolmae.marketry.common.event.UserSignedUpEvent;
import dev.eolmae.marketry.domain.custom.entity.CustomValueTierThreshold;
import dev.eolmae.marketry.domain.custom.entity.UserPreference;
import dev.eolmae.marketry.domain.custom.repository.CustomValueTierThresholdRepository;
import dev.eolmae.marketry.domain.custom.repository.UserPreferenceRepository;
import dev.eolmae.marketry.domain.custom.service.ProtectedSectorService;
import dev.eolmae.marketry.domain.custom.service.SuspendedStockClassificationService;
import lombok.RequiredArgsConstructor;
import org.springframework.context.event.EventListener;
import org.springframework.jdbc.core.ConnectionCallback;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class SignupInitializationService {

    public static final long INDUSTRY_USER_SYNC_LOCK = 941270361L;
    private static final long TEMPLATE_USER_ID = 1L;

    private final JdbcTemplate jdbcTemplate;
    private final CustomValueTierThresholdRepository customValueTierThresholdRepository;
    private final UserPreferenceRepository userPreferenceRepository;
    private final ProtectedSectorService protectedSectorService;
    private final SuspendedStockClassificationService suspendedStockClassificationService;

    /** 템플릿 계정(user_id = 1)의 시가총액 구간만 신규 사용자에게 복제한다. 업종 분류, 종목 배정, 별칭, 스냅샷 이력, 저장 설정, 색상 구간은 복제하지 않아 빈 상태로 시작한다. */
    @EventListener
    @Transactional
    public void onUserSignedUp(UserSignedUpEvent event) {
        Long userId = event.userId();
        // pg_advisory_xact_lock — 가입·업종·신규 종목 전파를 직렬화하는 PostgreSQL 세션 락. JPQL/QueryDSL에는
        // 대응하는 문법이 없다.
        jdbcTemplate.execute((ConnectionCallback<Void>) connection -> {
            try (var statement = connection.prepareStatement("SELECT pg_advisory_xact_lock(?)")) {
                statement.setLong(1, INDUSTRY_USER_SYNC_LOCK);
                statement.execute();
            }
            return null;
        });

        customValueTierThresholdRepository.saveAll(
                customValueTierThresholdRepository.findAllByUserIdOrderByThresholdValueAsc(TEMPLATE_USER_ID).stream()
                        .map(source -> CustomValueTierThreshold.create(
                                userId, source.getLabel(), source.getThresholdValue(), source.isExcludedByDefault()))
                        .toList());
        copyPreference(userId);
        // 새 회원에게도 보호 업종(신규 상장·거래 정지·미분류)이 처음부터 있게 한다.
        protectedSectorService.ensureForUser(userId);
        // 지금 거래 정지로 제외 중인 종목도 처음부터 거래 정지 업종에 둔다.
        suspendedStockClassificationService.applyCurrentExclusionsToUser(userId);
    }

    private void copyPreference(Long userId) {
        // 설정과 색상 구간은 템플릿에서 복제하지 않는다 — 비어 있으면 화면이 코드의 기본값(비로그인과 같은 값)을 쓴다.
        userPreferenceRepository.save(UserPreference.createEmpty(userId));
    }
}
