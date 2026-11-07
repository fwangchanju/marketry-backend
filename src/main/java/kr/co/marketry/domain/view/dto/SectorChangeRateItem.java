package kr.co.marketry.domain.view.dto;

import java.util.List;

public record SectorChangeRateItem(
        Long sectorId, String sectorName, int depth, List<SectorTierBreakdown> now, List<SectorTierBreakdown> before) {

    // depth 0은 실제 대분류 값이라 "아직 섹터를 안 붙였다"는 뜻으로 쓸 수 없다 — 대분류 판정
    // (item.depth() == 0)이 장식 안 된 항목까지 대분류로 잘못 고르게 된다. -1은 어떤 depth 필터에도
    // 걸리지 않는다.
    public static SectorChangeRateItem withoutBefore(Long sectorId, List<SectorTierBreakdown> now) {
        return new SectorChangeRateItem(sectorId, null, -1, now, null);
    }

    public static SectorChangeRateItem withBefore(
            Long sectorId, List<SectorTierBreakdown> now, List<SectorTierBreakdown> before) {
        return new SectorChangeRateItem(sectorId, null, -1, now, before);
    }

    public SectorChangeRateItem withSector(String sectorName, int depth) {
        return new SectorChangeRateItem(sectorId, sectorName, depth, now, before);
    }
}
