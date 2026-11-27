package kr.co.marketry.domain.stock.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.util.List;

// 야후 여러 종목 차트(spark) 응답에서 장 밖 시세에 필요한 값만 읽는다. 한 번에 20종목까지 받는다.
@JsonIgnoreProperties(ignoreUnknown = true)
public record YahooSparkResponse(Spark spark) {
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Spark(List<Result> result) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Result(String symbol, List<Response> response) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Response(Meta meta, List<Long> timestamp, Indicators indicators) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Indicators(List<Closes> quote) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Closes(List<Double> close) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Meta(Double previousClose, Double chartPreviousClose, TradingPeriods currentTradingPeriod) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record TradingPeriods(Period pre, Period regular, Period post) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Period(Long start, Long end) {}
}
