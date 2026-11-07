package kr.co.marketry.domain.stock.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

public record TossTokenResponse(
        @JsonProperty("access_token") String accessToken,
        @JsonProperty("expires_in") Long expiresIn) {
    @Override
    public String toString() {
        return "TossTokenResponse[token=redacted]";
    }
}
