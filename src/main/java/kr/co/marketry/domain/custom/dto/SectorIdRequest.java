package kr.co.marketry.domain.custom.dto;

import jakarta.validation.constraints.NotNull;

public record SectorIdRequest(
        @NotNull(message = "섹터를 선택해주세요.") Long sectorId) {}
