package kr.co.marketry.domain.custom.dto;

import java.util.List;
import kr.co.marketry.domain.custom.enums.SectorDeleteBlockReason;

/**
 * 업종 삭제 미리보기. 삭제할 수 있으면 담긴 종목 전부가 미분류로 옮겨지고(moveStockCount), 삭제할 수 없으면 blockReason이 이유다.
 * blockingStocks·deletableSectors는 예전 응답과 호환하려고 남겨 두었고 항상 빈 목록이다.
 */
public record SectorDeletePreview(
        String sectorName,
        boolean deletable,
        List<StockSectorItem> blockingStocks,
        List<String> deletableSectors,
        int moveStockCount,
        SectorDeleteBlockReason blockReason,
        List<String> subSectorNames) {

    public static SectorDeletePreview blocked(
            String sectorName, SectorDeleteBlockReason reason, List<String> subSectorNames) {
        return new SectorDeletePreview(sectorName, false, List.of(), List.of(), 0, reason, subSectorNames);
    }

    public static SectorDeletePreview deletable(String sectorName, int moveStockCount) {
        return new SectorDeletePreview(sectorName, true, List.of(), List.of(), moveStockCount, null, List.of());
    }
}
