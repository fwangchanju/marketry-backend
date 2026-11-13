package kr.co.marketry.domain.stock.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.util.List;

// 야후 차트 응답에서 정규장 종료 시각과 일봉 날짜만 읽는다. 종료 시각은 그날 정규장이 끝나는 시각(조기 마감일이면 그 시각)이다.
@JsonIgnoreProperties(ignoreUnknown = true)
public record YahooSessionResponse(Chart chart) {
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Chart(List<Result> result) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Result(Meta meta, List<Long> timestamp) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Meta(CurrentTradingPeriod currentTradingPeriod) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record CurrentTradingPeriod(Period regular) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Period(Long end) {}
}
