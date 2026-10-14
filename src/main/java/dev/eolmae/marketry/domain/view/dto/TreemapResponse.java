package dev.eolmae.marketry.domain.view.dto;

import java.time.LocalDateTime;
import java.util.List;

/**
 * SnapshotResponse<T>를 그대로 못 쓰는 이유: marketOverview는 트리 items와 별개로 마켓 전체 단위 값 하나라,
 * 여러 엔드포인트가 공유하는 제네릭 래퍼에 이 필드만을 위해 얹을 수 없다. market이 ALL_STOCK처럼 마켓
 * 여럿을 합친 조회면 단일 지수값이 없으므로 marketOverview는 null.
 * taxonomyUpdatedAt은 선택한 분류의 갱신 시각이다. 커스텀은 그 분류 데이터 주인(MARKETRY는 고정본, 내 분류은 본인)의 업종 정보·종목 배정 수정 시각,
 * 거래소는 활성 주권 종목 정보의 동기화 시각이며 해당 데이터가 없으면 null.
 */
public record TreemapResponse(
        LocalDateTime snapshotTime,
        List<TreemapSectorNode> items,
        MarketOverviewItem marketOverview,
        LocalDateTime taxonomyUpdatedAt,
        LocalDateTime afterHoursStart) {
    public static TreemapResponse empty() {
        return new TreemapResponse(null, List.of(), null, null, null);
    }
}
