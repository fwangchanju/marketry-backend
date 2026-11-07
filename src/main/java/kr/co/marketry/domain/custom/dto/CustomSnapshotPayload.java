package kr.co.marketry.domain.custom.dto;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import kr.co.marketry.domain.custom.enums.ColorLabel;

public record CustomSnapshotPayload(
        int snapshotVersion,
        List<Sector> sectors,
        List<StockAssignment> assignments,
        List<StockAlias> aliases,
        List<ScaleThreshold> scaleThresholds,
        List<ValueTierThreshold> valueTierThresholds,
        Map<String, Object> preferences) {

    public record Sector(Long id, Long parentId, String name, int depth, boolean excluded, UUID stableKey) {}

    public record StockAssignment(String stockCode, Long sectorId) {}

    public record StockAlias(String stockCode, String alias) {}

    public record ScaleThreshold(BigDecimal thresholdPercent, String color, ColorLabel colorLabel) {}

    public record ValueTierThreshold(String label, Long thresholdValue, boolean excludedByDefault) {}
}
