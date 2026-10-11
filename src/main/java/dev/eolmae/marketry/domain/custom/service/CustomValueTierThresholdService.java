package dev.eolmae.marketry.domain.custom.service;

import dev.eolmae.marketry.domain.auth.service.CurrentUser;
import dev.eolmae.marketry.domain.custom.dto.CustomValueTierItem;
import dev.eolmae.marketry.domain.custom.entity.CustomValueTierThreshold;
import dev.eolmae.marketry.domain.custom.repository.CustomValueTierThresholdRepository;
import java.math.BigDecimal;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 시가총액 구간(초대형주/대형주/...) 분류 — 코드 enum이 아니라 custom_value_tier_threshold 데이터 기준.
 * 사용자가 직접 구간을 설정한 적이 있으면 그 값을, 하나도 없으면(가입 직후 등) 백엔드 상수로 폴백한다
 * (색상 스케일과 같은 구조지만, 기본값 자체는 시가총액 구간 계산에 백엔드가 관여하므로 프론트가 아니라
 * 여기서 관리한다). id/label/값은 프론트와 텔레그램 캡션 제외 로직이 그대로 참조하므로 바꾸지 않는다. */
@Service
@Transactional(readOnly = true)
@RequiredArgsConstructor
public class CustomValueTierThresholdService {

    // thresholdValue 오름차순 고정 — resolveTier가 오름차순을 전제로 순회한다. 사용자가 구간을 하나도
    // 설정하지 않았을 때(비로그인 공개 엔드포인트 포함)의 폴백 값이다.
    private static final List<CustomValueTierThreshold> DEFAULT_TIERS = List.of(
            CustomValueTierThreshold.createDefault(4L, "소형주", 0L, true),
            CustomValueTierThreshold.createDefault(3L, "중형주", 500_000_000_000L, false),
            CustomValueTierThreshold.createDefault(2L, "대형주", 5_000_000_000_000L, false),
            CustomValueTierThreshold.createDefault(1L, "Top 2", 500_000_000_000_000L, false));

    private final CustomValueTierThresholdRepository customValueTierThresholdRepository;

    public List<CustomValueTierThreshold> findAllSortedAscending() {
        Long userId = CurrentUser.currentId();
        if (userId != null) {
            return findAllSortedAscending(userId);
        }
        return findDefaultSortedAscending();
    }

    /** 사용자가 구간을 직접 설정했으면 그 값을, 하나도 없으면 백엔드 상수로 폴백한다. */
    public List<CustomValueTierThreshold> findAllSortedAscending(Long userId) {
        List<CustomValueTierThreshold> userTiers =
                customValueTierThresholdRepository.findAllByUserIdOrderByThresholdValueAsc(userId);
        if (userTiers.isEmpty()) {
            return findDefaultSortedAscending();
        }
        return userTiers;
    }

    public List<CustomValueTierThreshold> findDefaultSortedAscending() {
        return DEFAULT_TIERS;
    }

    /** 프론트 필터 UI(시가총액 구간 슬라이더)가 하드코딩 대신 조회해서 그리는 용도. */
    public List<CustomValueTierItem> getValueTiers() {
        return toItems(findAllSortedAscending());
    }

    public List<CustomValueTierItem> getDefaultValueTiers() {
        return toItems(findDefaultSortedAscending());
    }

    public List<CustomValueTierItem> getValueTiers(Long userId) {
        return toItems(findAllSortedAscending(userId));
    }

    private List<CustomValueTierItem> toItems(List<CustomValueTierThreshold> tiers) {
        return tiers.stream()
                .map(tier -> new CustomValueTierItem(
                        tier.getId(), tier.getLabel(), tier.getThresholdValue(), tier.isExcludedByDefault()))
                .toList();
    }

    /** sortedTier 기준으로 totalMarketValue가 속하는 가장 높은 구간의 label. 가장 낮은 구간의
     * threshold보다도 작으면 가장 낮은 구간으로 분류한다(미분류로 남기지 않음). */
    public String resolveTier(List<CustomValueTierThreshold> sortedTier, BigDecimal totalMarketValue) {
        String label = sortedTier.getFirst().getLabel();
        for (CustomValueTierThreshold threshold : sortedTier) {
            if (threshold.isNotReachedBy(totalMarketValue)) {
                break;
            }
            label = threshold.getLabel();
        }
        return label;
    }
}
