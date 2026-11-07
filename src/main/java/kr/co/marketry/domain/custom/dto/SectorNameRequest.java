package kr.co.marketry.domain.custom.dto;

import jakarta.validation.constraints.NotBlank;

public record SectorNameRequest(
        @NotBlank(message = "섹터명을 입력해주세요.") String name) {}
