package dev.eolmae.marketry.domain.custom.dto;

import jakarta.validation.constraints.Size;

public record AliasRequest(
        @Size(max = 12, message = "약칭은 12자 이하로 입력해주세요.") String alias) {}
