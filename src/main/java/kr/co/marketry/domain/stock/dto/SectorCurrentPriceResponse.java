package kr.co.marketry.domain.stock.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;

// ka20001: 업종현재가요청
@JsonIgnoreProperties(ignoreUnknown = true)
public record SectorCurrentPriceResponse(
        @JsonProperty("return_code") String returnCode,
        @JsonProperty("return_msg") String returnMsg,
        @JsonProperty("cur_prc") String curPrc,
        @JsonProperty("pred_pre_sig") String predPreSig,
        @JsonProperty("pred_pre") String predPre,
        @JsonProperty("flu_rt") String fluRt,
        @JsonProperty("trde_prica") String trdePrica,
        @JsonProperty("trde_qty") String trdeQty,
        @JsonProperty("trde_frmatn_stk_num") String trdeFramtnStkNum,
        @JsonProperty("open_pric") String openPric,
        @JsonProperty("high_pric") String highPric,
        @JsonProperty("low_pric") String lowPric,
        @JsonProperty("mrkt_stat_cls_code") String mrktStatClsCode,
        @JsonProperty("upl") String upl,
        @JsonProperty("rising") String rising,
        @JsonProperty("stdns") String stdns,
        @JsonProperty("fall") String fall,
        @JsonProperty("lst") String lst,
        @JsonProperty("inds_cur_prc_tm") List<PriceTick> priceTicks)
        implements KiwoomResponse {

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record PriceTick(
            @JsonProperty("tm_n") String tmN,
            @JsonProperty("cur_prc_n") String curPrcN,
            @JsonProperty("flu_rt_n") String fluRtN,
            @JsonProperty("trde_qty_n") String trdeQtyN) {}
}
