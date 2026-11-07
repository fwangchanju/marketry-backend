package kr.co.marketry.domain.stock.client;

import java.math.BigDecimal;
import java.time.Instant;
import kr.co.marketry.domain.stock.dto.YahooChartResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

/**
 * 야후 파이낸스 차트 주소에서 도쿄 거래소 종목의 시세를 받는다.
 *
 * <p>공식 데이터 제공 API가 아니라서 주소나 응답 모양이 바뀌거나 막힐 수 있다. 응답이 비정상이면 예외를 던져 호출부가
 * 기존 시세를 유지하게 한다.
 */
@Component
@RequiredArgsConstructor
public class YahooFinanceQuoteClient {

    private static final String CHART_URL =
            "https://query1.finance.yahoo.com/v8/finance/chart/{symbol}?interval=1d&range=1d";
    private static final String TOKYO_SUFFIX = ".T";
    private static final String USER_AGENT = "Mozilla/5.0";

    private final RestClient restClient;

    public Quote fetchTokyoQuote(String stockCode) {
        YahooChartResponse response = restClient
                .get()
                .uri(CHART_URL, stockCode + TOKYO_SUFFIX)
                .header(HttpHeaders.USER_AGENT, USER_AGENT)
                .retrieve()
                .body(YahooChartResponse.class);
        return toQuote(stockCode, response);
    }

    static Quote toQuote(String stockCode, YahooChartResponse response) {
        if (response == null
                || response.chart() == null
                || response.chart().result() == null
                || response.chart().result().isEmpty()
                || response.chart().result().getFirst().meta() == null) {
            throw new IllegalStateException("야후 응답에 시세가 없습니다: " + stockCode);
        }
        YahooChartResponse.Meta meta = response.chart().result().getFirst().meta();
        if (meta.regularMarketPrice() == null
                || meta.chartPreviousClose() == null
                || meta.regularMarketTime() == null
                || meta.regularMarketPrice() <= 0
                || meta.chartPreviousClose() <= 0) {
            throw new IllegalStateException("야후 시세 값이 비정상입니다: " + stockCode);
        }
        return new Quote(
                BigDecimal.valueOf(meta.regularMarketPrice()),
                BigDecimal.valueOf(meta.chartPreviousClose()),
                Instant.ofEpochSecond(meta.regularMarketTime()));
    }

    public record Quote(BigDecimal currentPrice, BigDecimal previousClose, Instant marketTime) {}
}
