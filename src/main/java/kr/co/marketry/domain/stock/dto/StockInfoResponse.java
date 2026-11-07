package kr.co.marketry.domain.stock.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;

// ka10099: 종목정보 리스트
@JsonIgnoreProperties(ignoreUnknown = true)
public record StockInfoResponse(
        @JsonProperty("return_code") String returnCode,
        @JsonProperty("return_msg") String returnMsg,
        @JsonProperty("list") List<StockItem> list)
        implements KiwoomResponse {

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record StockItem(
            @JsonProperty("code") String code,
            @JsonProperty("name") String name,
            @JsonProperty("marketCode") String marketCode,
            @JsonProperty("upName") String upName,
            @JsonProperty("listCount") String listCount,
            @JsonProperty("lastPrice") String lastPrice,
            // NXT 거래 가능 여부 — "Y"만 가능이다(문서에 다른 값의 뜻은 없다).
            @JsonProperty("nxtEnable") String nxtEnable) {}
}
