package kr.co.marketry.domain.stock.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;

// NEXTRADE 매매체결대상종목 조회(/trdisuChg/trdisuChgList.do) 응답 — 종목별 편입/편출 상태.
@JsonIgnoreProperties(ignoreUnknown = true)
public record NextradeStockListResponse(
        @JsonProperty("trdisuChgList") List<Item> list) {

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Item(
            // "A005930"처럼 앞에 A가 붙은 단축코드
            @JsonProperty("isuSrdCd") String shortCode,
            // 편입/편출이 반영된 일자(yyyyMMdd)
            @JsonProperty("aggDd") String date,
            // "편입" 또는 "편출"
            @JsonProperty("addExlCd") String status) {}
}
