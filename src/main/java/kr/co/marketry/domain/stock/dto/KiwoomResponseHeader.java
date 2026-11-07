package kr.co.marketry.domain.stock.dto;

import org.springframework.http.HttpHeaders;

public record KiwoomResponseHeader(String apiId, String contYn, String nextKey) {

    private static final String HAS_NEXT = "Y";

    public static KiwoomResponseHeader from(HttpHeaders headers) {
        return new KiwoomResponseHeader(
                headers.getFirst("api-id"), headers.getFirst("cont-yn"), headers.getFirst("next-key"));
    }

    public boolean hasNext() {
        return HAS_NEXT.equals(contYn);
    }
}
