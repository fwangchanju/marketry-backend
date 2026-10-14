package dev.eolmae.marketry.domain.custom.service;

import dev.eolmae.marketry.domain.auth.service.CurrentUser;
import dev.eolmae.marketry.domain.custom.entity.CustomValueTierThreshold;
import dev.eolmae.marketry.domain.notification.properties.MarketryProperties;
import dev.eolmae.marketry.domain.view.dto.SectorTierBreakdown;
import dev.eolmae.marketry.domain.view.dto.SnapshotAverages;
import dev.eolmae.marketry.domain.view.dto.TreemapItem;
import dev.eolmae.marketry.domain.view.dto.TreemapSectorNode;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 마켓맵 섹터별(하위 섹터 재귀 포함) × 시가총액 구간별 등락률 원시 합계(분자/분모) 계산.
 * 트리 자체는 호출부가 TreemapQueryService로 이미 만들어서 넘겨준다 — 이 서비스가 TreemapQueryService를
 * 직접 의존하면, TreemapQueryService가 이 결과를 받아 응답에 채워 넣을 때(반대 방향 의존) 순환 참조가 된다.
 */
@Service
@Transactional(readOnly = true)
@RequiredArgsConstructor
public class SectorTierAggregationService {

    private static final int SCALE = 4;

    private final CustomValueTierThresholdService customValueTierThresholdService;
    private final MarketryProperties marketryProperties;

    /** 트리(하위 섹터 재귀 포함)를 섹터 id별 시가총액 구간별 등락률 원시 합계로 묶는다 —
     * 섹터 id → 구간별 원시 합계 맵을 돌려준다.
     * items가 하나도 없는 섹터(자신과 하위 전부 빈 경우)는 결과 맵에
     * 아예 없다. 리포지토리를 직접 보지 않고 CustomValueTierThresholdService를 거치는 이유 —
     * 사용자가 구간을 하나도 설정하지 않은 경우(가입 직후 등) 서비스가 백엔드 상수로 폴백해주는데,
     * 리포지토리를 직접 호출하면 그 폴백 없이 빈 목록을 받아 아래 toBreakdown에서 널 참조가 난다. */
    public Map<Long, List<SectorTierBreakdown>> aggregateBySector(List<TreemapSectorNode> tree) {
        Long userId = marketryProperties.userIdOrPublished(CurrentUser.currentId());
        Map<String, CustomValueTierThreshold> tierByLabel =
                customValueTierThresholdService.findAllSortedAscending(userId).stream()
                        .collect(Collectors.toMap(CustomValueTierThreshold::getLabel, Function.identity()));
        Map<Long, List<SectorTierBreakdown>> breakdownsBySectorId = new HashMap<>();
        collectBreakdowns(tree, tierByLabel, breakdownsBySectorId);
        return breakdownsBySectorId;
    }

    /** 전달받은 구간별 원시값을 전부 합산한 뒤 마지막에 한 번만 나눈 최종 가중/산술평균 — 화면 필터와
     * 무관하게 항상 전체 구간 기준이 필요한 호출부(텔레그램 캡션 등)용. 이미 나뉜 평균끼리 다시 평균내면
     * 구간별 종목 수/시총 비중을 알 수 없어 틀리기 때문에, 반드시 원시값 합산 후 나눗셈 순서를 지킨다. */
    public SnapshotAverages combine(List<SectorTierBreakdown> breakdowns) {
        BigDecimal weightedSum = BigDecimal.ZERO;
        BigDecimal totalValue = BigDecimal.ZERO;
        BigDecimal simpleSum = BigDecimal.ZERO;
        int itemCount = 0;
        for (SectorTierBreakdown breakdown : breakdowns) {
            weightedSum = weightedSum.add(breakdown.weightedSum());
            totalValue = totalValue.add(breakdown.totalValue());
            simpleSum = simpleSum.add(breakdown.simpleSum());
            itemCount += breakdown.itemCount();
        }
        BigDecimal weightedAvg = BigDecimal.ZERO;
        if (totalValue.signum() != 0) {
            weightedAvg = weightedSum.divide(totalValue, SCALE, RoundingMode.HALF_UP);
        }
        BigDecimal simpleAvg = BigDecimal.ZERO;
        if (itemCount != 0) {
            simpleAvg = simpleSum.divide(BigDecimal.valueOf(itemCount), SCALE, RoundingMode.HALF_UP);
        }
        return new SnapshotAverages(weightedAvg, simpleAvg);
    }

    private void collectBreakdowns(
            List<TreemapSectorNode> nodes,
            Map<String, CustomValueTierThreshold> tierByLabel,
            Map<Long, List<SectorTierBreakdown>> out) {
        for (TreemapSectorNode node : nodes) {
            Map<String, List<TreemapItem>> itemsByTierLabel =
                    collectItems(node).stream().collect(Collectors.groupingBy(TreemapItem::marketValueTier));
            List<SectorTierBreakdown> breakdowns = itemsByTierLabel.entrySet().stream()
                    .map(entry -> toBreakdown(tierByLabel.get(entry.getKey()), entry.getValue()))
                    .toList();
            if (!breakdowns.isEmpty()) {
                out.put(node.sectorId(), breakdowns);
            }
            collectBreakdowns(node.children(), tierByLabel, out);
        }
    }

    private SectorTierBreakdown toBreakdown(CustomValueTierThreshold tier, List<TreemapItem> items) {
        RawSums sums = computeRawSums(items);
        return new SectorTierBreakdown(
                tier.getId(),
                tier.getLabel(),
                sums.weightedSum(),
                sums.totalValue(),
                sums.simpleSum(),
                sums.itemCount());
    }

    private List<TreemapItem> collectItems(TreemapSectorNode node) {
        List<TreemapItem> items = new ArrayList<>(node.items());
        for (TreemapSectorNode child : node.children()) {
            items.addAll(collectItems(child));
        }
        return items;
    }

    // 가중평균의 분자(Σ등락률×시총)/분모(Σ시총), 산술평균의 분자(Σ등락률)/분모(종목 수)를 나누지 않고
    // 원시값 그대로 한 번의 순회로 구한다 — 여러 구간을 조합할 땐 이미 나뉜 평균끼리 다시 평균내면 틀리므로,
    // 나눗셈은 조회 시점에 필요한 구간들을 합산한 뒤 마지막에 한 번만 한다.
    private RawSums computeRawSums(List<TreemapItem> items) {
        BigDecimal totalValue = BigDecimal.ZERO;
        BigDecimal weightedSum = BigDecimal.ZERO;
        BigDecimal simpleSum = BigDecimal.ZERO;
        for (TreemapItem item : items) {
            totalValue = totalValue.add(item.totalMarketValue());
            weightedSum = weightedSum.add(item.changeRate().multiply(item.totalMarketValue()));
            simpleSum = simpleSum.add(item.changeRate());
        }
        return new RawSums(weightedSum, totalValue, simpleSum, items.size());
    }

    private record RawSums(BigDecimal weightedSum, BigDecimal totalValue, BigDecimal simpleSum, int itemCount) {}
}
