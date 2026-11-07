package kr.co.marketry.domain.stock.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

public record TokenResponse(
        @JsonProperty("return_code") int returnCode,
        @JsonProperty("return_msg") String returnMsg,
        @JsonProperty("token_type") String tokenType,
        @JsonProperty("token") String token,
        @JsonProperty("expires_dt") String expiresAt // yyyyMMddHHmmss
        ) {}
