package kr.co.marketry.domain.custom.dto;

import java.util.List;

/** failedStockCodes가 비어있으면 전부 반영된 것, 값이 있으면 그 종목들만 반영되지 않은 것. */
public record BulkAssignResponse(List<String> failedStockCodes, Long sectorId) {}
