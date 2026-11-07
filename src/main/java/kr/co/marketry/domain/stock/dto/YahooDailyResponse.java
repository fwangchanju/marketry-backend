package kr.co.marketry.domain.stock.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.util.List;

// 야후 차트의 날짜별 값. 가격은 분할을 반영한 값으로 온다.
@JsonIgnoreProperties(ignoreUnknown = true)
public record YahooDailyResponse(Chart chart) {
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Chart(List<Result> result) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Result(List<Long> timestamp, Indicators indicators) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Indicators(List<Quote> quote) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Quote(
            List<Double> open, List<Double> high, List<Double> low, List<Double> close, List<Long> volume) {}
}
