package kr.co.marketry.domain.stock.client;

import java.math.BigDecimal;
import java.time.DateTimeException;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import kr.co.marketry.common.enums.Zone;
import kr.co.marketry.domain.stock.dto.YahooChartResponse;
import kr.co.marketry.domain.stock.dto.YahooDailyResponse;
import kr.co.marketry.domain.stock.dto.YahooSessionResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.core.ParameterizedTypeReference;
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
    private static final String DAILY_URL =
            "https://query1.finance.yahoo.com/v8/finance/chart/{symbol}?interval=1d&period1={from}&period2={to}";
    private static final String SESSION_URL =
            "https://query1.finance.yahoo.com/v8/finance/chart/{symbol}?interval=1d&range=5d";
    private static final String MARKET_VALUE_URL =
            "https://query1.finance.yahoo.com/ws/fundamentals-timeseries/v1/finance/timeseries/{symbol}"
                    + "?type=trailingMarketCap&period1={from}&period2={to}";
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

    /** 접미사 없는 심볼(미국 종목)의 시세. 종목 코드 그대로 야후에 요청한다. */
    public Quote fetchQuote(String symbol) {
        YahooChartResponse response = restClient
                .get()
                .uri(CHART_URL, symbol)
                .header(HttpHeaders.USER_AGENT, USER_AGENT)
                .retrieve()
                .body(YahooChartResponse.class);
        return toQuote(symbol, response);
    }

    /** 지수의 가장 최근 일봉 날짜와 그날 정규장 종료 시각. 장중이면 오늘 봉이 최신으로 온다. */
    public IndexSession fetchIndexSession(String symbol) {
        YahooSessionResponse response = restClient
                .get()
                .uri(SESSION_URL, symbol)
                .header(HttpHeaders.USER_AGENT, USER_AGENT)
                .retrieve()
                .body(YahooSessionResponse.class);
        return toIndexSession(symbol, response);
    }

    static IndexSession toIndexSession(String symbol, YahooSessionResponse response) {
        if (response == null
                || response.chart() == null
                || response.chart().result() == null
                || response.chart().result().isEmpty()) {
            throw new IllegalStateException("야후 응답에 장 시간이 없습니다: " + symbol);
        }
        YahooSessionResponse.Result result = response.chart().result().getFirst();
        if (result.timestamp() == null
                || result.timestamp().isEmpty()
                || result.meta() == null
                || result.meta().currentTradingPeriod() == null
                || result.meta().currentTradingPeriod().regular() == null
                || result.meta().currentTradingPeriod().regular().end() == null) {
            throw new IllegalStateException("야후 응답에 장 시간이 없습니다: " + symbol);
        }
        return new IndexSession(
                Instant.ofEpochSecond(result.timestamp().getLast())
                        .atZone(Zone.KST.zoneId())
                        .toLocalDate(),
                Instant.ofEpochSecond(
                        result.meta().currentTradingPeriod().regular().end()));
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

    /** 그 기간의 일봉. 값이 빈 날은 뺀다. 가격은 야후가 분할을 반영해 준 값이다. */
    public List<DailyBar> fetchDailyBars(String symbol, LocalDate from, LocalDate to) {
        YahooDailyResponse response = restClient
                .get()
                .uri(
                        DAILY_URL,
                        symbol,
                        from.atStartOfDay(Zone.KST.zoneId()).toEpochSecond(),
                        to.plusDays(1).atStartOfDay(Zone.KST.zoneId()).toEpochSecond())
                .header(HttpHeaders.USER_AGENT, USER_AGENT)
                .retrieve()
                .body(YahooDailyResponse.class);
        return toDailyBars(symbol, response);
    }

    static List<DailyBar> toDailyBars(String symbol, YahooDailyResponse response) {
        if (response == null
                || response.chart() == null
                || response.chart().result() == null
                || response.chart().result().isEmpty()) {
            throw new IllegalStateException("야후 응답에 일봉이 없습니다: " + symbol);
        }
        YahooDailyResponse.Result result = response.chart().result().getFirst();
        if (result.timestamp() == null
                || result.indicators() == null
                || result.indicators().quote() == null
                || result.indicators().quote().isEmpty()) {
            throw new IllegalStateException("야후 응답에 일봉이 없습니다: " + symbol);
        }
        YahooDailyResponse.Quote quote = result.indicators().quote().getFirst();
        List<DailyBar> bars = new ArrayList<>();
        for (int index = 0; index < result.timestamp().size(); index++) {
            Double close = quote.close().get(index);
            // 종가가 없는 봉은 뺀다. 시가·고가·저가만 비어 있는 봉은 종가로 채워 살린다 — 종목 수집은 종가만 쓰는데 그런 봉까지 버리면
            // 그 종목이 하루 빠지거나 전일 종가가 이틀 전으로 밀려 등락률이 틀어진다. 지수 일봉도 종가만 쓰므로 채운 시가·고가·저가는 쓰이지 않는다.
            if (close == null || close <= 0) {
                continue;
            }
            Double open = valueOrClose(quote.open().get(index), close);
            Double high = valueOrClose(quote.high().get(index), close);
            Double low = valueOrClose(quote.low().get(index), close);
            Long volume = quote.volume() == null ? null : quote.volume().get(index);
            bars.add(new DailyBar(
                    Instant.ofEpochSecond(result.timestamp().get(index))
                            .atZone(Zone.KST.zoneId())
                            .toLocalDate(),
                    BigDecimal.valueOf(open),
                    BigDecimal.valueOf(high),
                    BigDecimal.valueOf(low),
                    BigDecimal.valueOf(close),
                    volume == null ? 0 : volume));
        }
        return bars;
    }

    /** 야후의 가장 최근 시가총액과 그 날짜. 차트 주소에 없어 재무 시계열 주소에서 받는다. */
    public MarketValue fetchLatestMarketValue(String stockCode, LocalDate from, LocalDate to) {
        return fetchMarketValue(stockCode + TOKYO_SUFFIX, stockCode, from, to);
    }

    /** 접미사 없는 심볼(미국 종목)의 가장 최근 시가총액과 그 날짜. */
    public MarketValue fetchMarketValueOf(String symbol, LocalDate from, LocalDate to) {
        return fetchMarketValue(symbol, symbol, from, to);
    }

    private MarketValue fetchMarketValue(String symbol, String stockCode, LocalDate from, LocalDate to) {
        Map<String, Object> body = restClient
                .get()
                .uri(
                        MARKET_VALUE_URL,
                        symbol,
                        from.atStartOfDay(Zone.KST.zoneId()).toEpochSecond(),
                        to.plusDays(1).atStartOfDay(Zone.KST.zoneId()).toEpochSecond())
                .header(HttpHeaders.USER_AGENT, USER_AGENT)
                .retrieve()
                .body(new ParameterizedTypeReference<Map<String, Object>>() {});
        return toMarketValue(stockCode, body);
    }

    @SuppressWarnings("unchecked")
    static MarketValue toMarketValue(String stockCode, Map<String, Object> body) {
        try {
            Map<String, Object> series = (Map<String, Object>) body.get("timeseries");
            List<Map<String, Object>> results = (List<Map<String, Object>>) series.get("result");
            List<Map<String, Object>> points =
                    (List<Map<String, Object>>) results.getFirst().get("trailingMarketCap");
            MarketValue latest = null;
            for (Map<String, Object> point : points) {
                if (point == null) {
                    continue;
                }
                LocalDate date = LocalDate.parse(String.valueOf(point.get("asOfDate")));
                Map<String, Object> reported = (Map<String, Object>) point.get("reportedValue");
                BigDecimal value = new BigDecimal(String.valueOf(reported.get("raw")));
                if (value.signum() > 0 && (latest == null || date.isAfter(latest.date()))) {
                    latest = new MarketValue(date, value);
                }
            }
            if (latest != null) {
                return latest;
            }
        } catch (ClassCastException | NullPointerException | NumberFormatException | DateTimeException e) {
            throw new IllegalStateException("야후 시가총액 응답이 비정상입니다: " + stockCode);
        }
        throw new IllegalStateException("야후 시가총액이 없습니다: " + stockCode);
    }

    private static Double valueOrClose(Double value, Double close) {
        return value == null || value <= 0 ? close : value;
    }

    public record Quote(BigDecimal currentPrice, BigDecimal previousClose, Instant marketTime) {}

    public record DailyBar(
            LocalDate date, BigDecimal open, BigDecimal high, BigDecimal low, BigDecimal close, long volume) {}

    public record MarketValue(LocalDate date, BigDecimal value) {}

    public record IndexSession(LocalDate latestBarDate, Instant regularEnd) {}
}
