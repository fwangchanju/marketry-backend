package kr.co.marketry.domain.stock.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

// ka20002: 업종별주가요청
@JsonIgnoreProperties(ignoreUnknown = true)
public record SectorPriceListResponse(
        @JsonProperty("return_code") String returnCode,
        @JsonProperty("return_msg") String returnMsg,
        @JsonProperty("inds_stkpc") List<StockItem> items)
        implements KiwoomResponse {

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record StockItem(
            @JsonProperty("stk_cd") String stkCd,
            @JsonProperty("stk_nm") String stkNm,
            @JsonProperty("cur_prc") String curPrc,
            @JsonProperty("pred_pre_sig") String predPreSig,
            @JsonProperty("pred_pre") String predPre,
            @JsonProperty("flu_rt") String fluRt) {}

    @Override
    public SectorPriceListResponse mergeNext(KiwoomResponse next) {
        List<StockItem> mergedItems = new ArrayList<>(items);
        mergedItems.addAll(((SectorPriceListResponse) next).items);
        return new SectorPriceListResponse(returnCode, returnMsg, mergedItems);
    }

    @Override
    public SectorPriceListResponse dedupe() {
        // 페이지 경계에서 동일 종목이 중복 반환되는 경우가 있어 stkCd 기준으로 중복 제거
        Map<String, StockItem> deduped = new HashMap<>();
        for (StockItem item : items) {
            deduped.putIfAbsent(item.stkCd(), item);
        }
        return new SectorPriceListResponse(returnCode, returnMsg, new ArrayList<>(deduped.values()));
    }
}
