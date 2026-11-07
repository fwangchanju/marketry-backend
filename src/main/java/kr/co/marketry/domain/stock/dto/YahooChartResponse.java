package kr.co.marketry.domain.stock.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.util.List;

// 야후 차트 응답에서 시세에 필요한 값만 읽는다.
@JsonIgnoreProperties(ignoreUnknown = true)
public record YahooChartResponse(Chart chart) {
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Chart(List<Result> result) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Result(Meta meta) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Meta(Double regularMarketPrice, Double chartPreviousClose, Long regularMarketTime) {}
}
