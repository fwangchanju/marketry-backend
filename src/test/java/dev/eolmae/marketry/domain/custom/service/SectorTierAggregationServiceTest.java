package dev.eolmae.marketry.domain.custom.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import dev.eolmae.marketry.domain.auth.enums.Role;
import dev.eolmae.marketry.domain.auth.service.AuthenticatedUserPrincipal;
import dev.eolmae.marketry.domain.custom.entity.CustomValueTierThreshold;
import dev.eolmae.marketry.domain.custom.repository.CustomValueTierThresholdRepository;
import dev.eolmae.marketry.domain.notification.properties.MarketryProperties;
import dev.eolmae.marketry.domain.view.dto.SectorTierBreakdown;
import dev.eolmae.marketry.domain.view.dto.SnapshotAverages;
import dev.eolmae.marketry.domain.view.dto.TreemapItem;
import dev.eolmae.marketry.domain.view.dto.TreemapSectorNode;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.util.ReflectionTestUtils;

class SectorTierAggregationServiceTest {

    private static final long TEST_USER_ID = 1L;
    // 비로그인 폴백 대상과 다른 값으로 둬서, 실제로 이 프로퍼티 값을 읽는지(우연히 일치하는 게
    // 아닌지)를 구분해 검증한다.
    private static final long PUBLISHED_PROPERTY_USER_ID = 555555L;

    private final CustomValueTierThresholdRepository marketValueTierThresholdRepository =
            mock(CustomValueTierThresholdRepository.class);
    private final CustomValueTierThresholdService customValueTierThresholdService =
            new CustomValueTierThresholdService(marketValueTierThresholdRepository);
    private final MarketryProperties marketryProperties =
            new MarketryProperties("http://localhost:8081", 999999L, PUBLISHED_PROPERTY_USER_ID);
    private final SectorTierAggregationService service =
            new SectorTierAggregationService(customValueTierThresholdService, marketryProperties);

    @BeforeEach
    void stubAuthentication() {
        var principal = new AuthenticatedUserPrincipal(TEST_USER_ID, Role.ADMIN);
        SecurityContextHolder.getContext()
                .setAuthentication(
                        new UsernamePasswordAuthenticationToken(principal, null, principal.getAuthorities()));
    }

    @AfterEach
    void clearAuthentication() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void aggregateBySector_비로그인이면_marketry_published_user_id_프로퍼티_값으로_구간을_조회한다() {
        SecurityContextHolder.clearContext();
        CustomValueTierThreshold large = tier(10L, "대형");
        when(marketValueTierThresholdRepository.findAllByUserIdOrderByThresholdValueAsc(PUBLISHED_PROPERTY_USER_ID))
                .thenReturn(List.of(large));

        TreemapSectorNode node = leaf(1L, "반도체", List.of(item("005930", "대형", BigDecimal.TEN, 10_000)));

        Map<Long, List<SectorTierBreakdown>> result = service.aggregateBySector(List.of(node));

        assertThat(result.get(1L)).extracting(SectorTierBreakdown::tierLabel).containsExactly("대형");
    }

    @Test
    void aggregateBySector_하위_섹터_항목이_부모_합계에_재귀로_포함된다() {
        CustomValueTierThreshold large = tier(10L, "대형");
        when(marketValueTierThresholdRepository.findAllByUserIdOrderByThresholdValueAsc(TEST_USER_ID))
                .thenReturn(List.of(large));

        TreemapSectorNode child = leaf(2L, "반도체 소재", List.of(item("000660", "대형", BigDecimal.TEN, 10_000)));
        TreemapSectorNode parent = new TreemapSectorNode(
                1L,
                "반도체",
                false,
                BigDecimal.ZERO,
                List.of(child),
                List.of(item("005930", "대형", BigDecimal.TEN, 10_000)));

        Map<Long, List<SectorTierBreakdown>> result = service.aggregateBySector(List.of(parent));

        // 부모(1L)는 자기 items(005930) + 자식(000660)까지 재귀로 합친 값을 가진다.
        SectorTierBreakdown parentBreakdown = result.get(1L).get(0);
        assertThat(parentBreakdown.totalValue()).isEqualByComparingTo(BigDecimal.valueOf(20_000));
        assertThat(parentBreakdown.itemCount()).isEqualTo(2);
        // 자식(2L)은 자기 items만 가진다.
        SectorTierBreakdown childBreakdown = result.get(2L).get(0);
        assertThat(childBreakdown.totalValue()).isEqualByComparingTo(BigDecimal.valueOf(10_000));
        assertThat(childBreakdown.itemCount()).isEqualTo(1);
    }

    @Test
    void aggregateBySector_시가총액_구간별로_따로_묶인다() {
        CustomValueTierThreshold large = tier(10L, "대형");
        CustomValueTierThreshold small = tier(20L, "소형");
        when(marketValueTierThresholdRepository.findAllByUserIdOrderByThresholdValueAsc(TEST_USER_ID))
                .thenReturn(List.of(large, small));

        TreemapSectorNode node = leaf(
                1L,
                "반도체",
                List.of(
                        item("005930", "대형", BigDecimal.TEN, 10_000),
                        item("000660", "소형", BigDecimal.valueOf(-5), 1_000)));

        Map<Long, List<SectorTierBreakdown>> result = service.aggregateBySector(List.of(node));

        assertThat(result.get(1L)).extracting(SectorTierBreakdown::tierLabel).containsExactlyInAnyOrder("대형", "소형");
        SectorTierBreakdown largeBreakdown = result.get(1L).stream()
                .filter(b -> b.tierId().equals(10L))
                .findFirst()
                .orElseThrow();
        assertThat(largeBreakdown.itemCount()).isEqualTo(1);
    }

    @Test
    void aggregateBySector_가중합과_산술합을_각각_구한다() {
        CustomValueTierThreshold large = tier(10L, "대형");
        when(marketValueTierThresholdRepository.findAllByUserIdOrderByThresholdValueAsc(TEST_USER_ID))
                .thenReturn(List.of(large));

        // 시총 90,000짜리 +30%, 시총 10,000짜리 +10% — 가중합은 시총에 끌리고 산술합은 종목당 등락률 합.
        TreemapSectorNode node = leaf(
                1L,
                "반도체",
                List.of(
                        item("005930", "대형", BigDecimal.valueOf(30), 90_000),
                        item("000660", "대형", BigDecimal.valueOf(10), 10_000)));

        Map<Long, List<SectorTierBreakdown>> result = service.aggregateBySector(List.of(node));

        SectorTierBreakdown breakdown = result.get(1L).get(0);
        // weightedSum = 30*90,000 + 10*10,000 = 2,800,000, totalValue = 100,000
        assertThat(breakdown.weightedSum()).isEqualByComparingTo(BigDecimal.valueOf(2_800_000));
        assertThat(breakdown.totalValue()).isEqualByComparingTo(BigDecimal.valueOf(100_000));
        // simpleSum = 30 + 10 = 40, itemCount = 2
        assertThat(breakdown.simpleSum()).isEqualByComparingTo(BigDecimal.valueOf(40));
        assertThat(breakdown.itemCount()).isEqualTo(2);
    }

    @Test
    void aggregateBySector_종목이_없는_섹터는_결과_맵에_없다() {
        when(marketValueTierThresholdRepository.findAllByUserIdOrderByThresholdValueAsc(TEST_USER_ID))
                .thenReturn(List.of());

        TreemapSectorNode empty = leaf(1L, "빈섹터", List.of());

        Map<Long, List<SectorTierBreakdown>> result = service.aggregateBySector(List.of(empty));

        assertThat(result).doesNotContainKey(1L);
    }

    @Test
    void combine_원시값을_합산한_뒤_한_번만_나눈다() {
        // 시총 10,000에 +10%p, 시총 40,000에 -2%p — 원시값을 합산한 뒤 나누면 +0.4가 맞다.
        SectorTierBreakdown tierA = new SectorTierBreakdown(
                10L, "대형", BigDecimal.valueOf(100_000), BigDecimal.valueOf(10_000), BigDecimal.TEN, 1);
        SectorTierBreakdown tierB = new SectorTierBreakdown(
                10L, "대형", BigDecimal.valueOf(-80_000), BigDecimal.valueOf(40_000), BigDecimal.valueOf(-2), 1);

        SnapshotAverages averages = service.combine(List.of(tierA, tierB));

        assertThat(averages.weightedAvgChangeRate()).isEqualByComparingTo(BigDecimal.valueOf(0.4));
        assertThat(averages.simpleAvgChangeRate()).isEqualByComparingTo(BigDecimal.valueOf(4));
    }

    @Test
    void combine_빈_목록이면_0을_반환한다() {
        SnapshotAverages averages = service.combine(List.of());

        assertThat(averages.weightedAvgChangeRate()).isEqualByComparingTo(BigDecimal.ZERO);
        assertThat(averages.simpleAvgChangeRate()).isEqualByComparingTo(BigDecimal.ZERO);
    }

    private CustomValueTierThreshold tier(Long id, String label) {
        CustomValueTierThreshold threshold = CustomValueTierThreshold.create(TEST_USER_ID, label, 0L, false);
        ReflectionTestUtils.setField(threshold, "id", id);
        return threshold;
    }

    private TreemapSectorNode leaf(Long sectorId, String sectorName, List<TreemapItem> items) {
        return new TreemapSectorNode(sectorId, sectorName, false, BigDecimal.ZERO, List.of(), items);
    }

    private TreemapItem item(String stockCode, String tierLabel, BigDecimal changeRate, long totalMarketValue) {
        return new TreemapItem(
                stockCode,
                stockCode,
                null,
                BigDecimal.TEN,
                BigDecimal.TEN,
                BigDecimal.valueOf(totalMarketValue),
                tierLabel,
                changeRate,
                LocalDateTime.of(2026, 7, 31, 10, 0));
    }
}
