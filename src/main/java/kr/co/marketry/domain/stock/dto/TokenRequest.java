package kr.co.marketry.domain.stock.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

public record TokenRequest(
        @JsonProperty("grant_type") String grantType,
        @JsonProperty("appkey") String appKey,
        @JsonProperty("secretkey") String secret) {

    public static TokenRequest of(String appKey, String secret) {
        return new TokenRequest("client_credentials", appKey, secret);
    }
}
