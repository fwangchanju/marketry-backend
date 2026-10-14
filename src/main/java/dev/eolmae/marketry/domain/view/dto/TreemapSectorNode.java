package dev.eolmae.marketry.domain.view.dto;

import java.math.BigDecimal;
import java.util.List;

public record TreemapSectorNode(
        Long sectorId,
        String sectorName,
        boolean isExcluded,
        BigDecimal totalMarketValue,
        List<TreemapSectorNode> children,
        List<TreemapItem> items) {

    // 기본 마켓맵 노드용 — exclude 대상 아님, 변화율 미계산, 자식 없음(1뎁스).
    public static TreemapSectorNode leaf(
            Long sectorId, String sectorName, BigDecimal totalMarketValue, List<TreemapItem> items) {
        return new TreemapSectorNode(sectorId, sectorName, false, totalMarketValue, List.of(), items);
    }
}
