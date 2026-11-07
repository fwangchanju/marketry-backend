package kr.co.marketry.domain.custom.dto;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import java.util.List;

public record BulkAssignRequest(
        @NotEmpty(message = "종목을 하나 이상 선택해주세요.") List<String> stockCodes,
        @NotNull(message = "섹터를 선택해주세요.") Long sectorId) {}
