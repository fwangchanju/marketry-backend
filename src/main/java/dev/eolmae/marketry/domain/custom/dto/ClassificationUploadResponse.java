package dev.eolmae.marketry.domain.custom.dto;

import java.util.List;

public record ClassificationUploadResponse(
        int createdSectorCount, List<AppliedAssignment> appliedAssignments, List<String> failedStockCodes) {

    public record AppliedAssignment(List<String> path, Long sectorId, List<String> stockCodes) {}
}
