package kr.co.marketry.domain.stock.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

public interface KiwoomResponse {

    @JsonProperty("return_code")
    String returnCode();

    @JsonProperty("return_msg")
    String returnMsg();

    default KiwoomResponse mergeNext(KiwoomResponse next) {
        return next;
    }

    // 페이지네이션 병합 완료 후 fetchAll() 끝에서 1회만 호출 — 페이지 경계 중복 제거용
    default KiwoomResponse dedupe() {
        return this;
    }
}
