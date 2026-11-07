package kr.co.marketry.domain.custom.dto;

import jakarta.validation.constraints.NotBlank;

public record SnapshotLabelRequest(
        @NotBlank(message = "버전명을 입력해주세요.") String label) {}
