package kr.co.marketry.domain.stock.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.util.List;

// 야후 차트 응답에서 시세에 필요한 값만 읽는다.
@JsonIgnoreProperties(ignoreUnknown = true)
public record YahooChartResponse(Chart chart) {
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Chart(List<Result> result) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Result(Meta meta, Indicators indicators) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Indicators(List<Bar> quote) {}

    /** 오늘 일봉 — 장중에도 그때까지의 시가·고가·저가·거래량이 온다. */
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Bar(List<Double> open, List<Double> high, List<Double> low, List<Long> volume) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Meta(Double regularMarketPrice, Double chartPreviousClose, Long regularMarketTime) {}
}
