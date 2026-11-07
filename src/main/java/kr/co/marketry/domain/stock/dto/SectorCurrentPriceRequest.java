package kr.co.marketry.domain.stock.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

// ka20001: 업종현재가요청
public record SectorCurrentPriceRequest(
        @JsonProperty("mrkt_tp") String mrktTp,
        @JsonProperty("inds_cd") String indsCd) implements KiwoomRequest {

    @Override
    public String path() {
        return "/api/dostk/sect";
    }

    @Override
    public String apiId() {
        return "ka20001";
    }
}
