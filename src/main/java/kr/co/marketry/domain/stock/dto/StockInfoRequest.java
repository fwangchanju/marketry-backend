package kr.co.marketry.domain.stock.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

// ka10099: 종목정보 리스트
public record StockInfoRequest(@JsonProperty("mrkt_tp") String mrktTp) implements KiwoomRequest {

    @Override
    public String path() {
        return "/api/dostk/stkinfo";
    }

    @Override
    public String apiId() {
        return "ka10099";
    }
}
