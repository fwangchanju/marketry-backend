package kr.co.marketry.domain.stock.client;

import java.math.BigDecimal;
import java.time.DateTimeException;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import kr.co.marketry.common.enums.Zone;
import kr.co.marketry.domain.stock.dto.YahooChartResponse;
import kr.co.marketry.domain.stock.dto.YahooDailyResponse;
import kr.co.marketry.domain.stock.dto.YahooSessionResponse;
import kr.co.marketry.domain.stock.dto.YahooSparkResponse;
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
    // 여러 종목의 오늘 5분 가격(장 밖 포함)과 전일 종가, 그날 프리·정규·애프터 시각. 한 번에 20종목까지 받는다.
    private static final String SPARK_URL = "https://query1.finance.yahoo.com/v7/finance/spark"
            + "?symbols={symbols}&range=1d&interval=5m&includePrePost=true";
    public static final int SPARK_MAX_SYMBOLS = 20;
    private static final long SPARK_SLOT_MINUTES = 5;
    private static final String TOKYO_SUFFIX = ".T";
    private static final String USER_AGENT = "Mozilla/5.0";

    private final RestClient restClient;

    // TODO 묶음 방식(fetchTokyoQuotes)이 운영에서 안정되면 삭제한다.
    public Quote fetchTokyoQuote(String stockCode) {
        YahooChartResponse response = restClient
                .get()
                .uri(CHART_URL, stockCode + TOKYO_SUFFIX)
                .header(HttpHeaders.USER_AGENT, USER_AGENT)
                .retrieve()
                .body(YahooChartResponse.class);
        return toQuote(stockCode, response);
    }

    /**
     * 도쿄 거래소 종목 여러 개(최대 {@link #SPARK_MAX_SYMBOLS}개)의 시세를 한 번에 받는다(2026-11-27). 응답에 없거나 값이 비정상인 종목은 결과에서 빠진다.
     * 오늘 일봉(시가·고가·저가·거래량)은 이 요청에 없어 비어 있다. 저녁 종가 확인이 일봉 표를 채운다.
     */
    public Map<String, Quote> fetchTokyoQuotes(List<String> stockCodes) {
        List<String> symbols =
                stockCodes.stream().map(code -> code + TOKYO_SUFFIX).toList();
        Instant now = Instant.now();
        Map<String, Quote> quotes = new HashMap<>();
        for (ExtendedQuote extended : fetchExtendedQuotes(symbols)) {
            Quote quote = toTokyoQuote(extended, now);
            if (quote != null) {
                quotes.put(extended.symbol().substring(0, extended.symbol().length() - TOKYO_SUFFIX.length()), quote);
            }
        }
        return quotes;
    }

    /**
     * 5분 가격의 마지막 값을 현재가로, 그 5분 칸의 끝(아직 진행 중이면 지금)을 체결 시각으로 쓴다. 칸 끝을 쓰는 것은 마감 직전 15:25 칸이 15:30
     * 종가 시세로 저장되게 하려는 것이다. 가격이 하나도 없으면 null이다. 개장 전에는 어제 칸이 와서 체결 시각이 어제 날짜가 되므로 오늘 시세로 세지 않는다.
     */
    static Quote toTokyoQuote(ExtendedQuote extended, Instant now) {
        if (extended.prices().isEmpty()) {
            return null;
        }
        PricePoint last = extended.prices().getLast();
        Instant slotEnd = last.time().plus(Duration.ofMinutes(SPARK_SLOT_MINUTES));
        return new Quote(last.price(), extended.previousClose(), slotEnd.isAfter(now) ? now : slotEnd);
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
                Instant.ofEpochSecond(meta.regularMarketTime()),
                todayBarOf(response.chart().result().getFirst().indicators()));
    }

    // 응답의 마지막 일봉. 값이 하나라도 비면 쓰지 않는다(일봉 없이도 시세는 저장한다).
    private static TodayBar todayBarOf(YahooChartResponse.Indicators indicators) {
        if (indicators == null
                || indicators.quote() == null
                || indicators.quote().isEmpty()) {
            return null;
        }
        YahooChartResponse.Bar bar = indicators.quote().getFirst();
        Double open = last(bar.open());
        Double high = last(bar.high());
        Double low = last(bar.low());
        Long volume = bar.volume() == null || bar.volume().isEmpty()
                ? null
                : bar.volume().getLast();
        if (open == null || high == null || low == null || volume == null || open <= 0 || high <= 0 || low <= 0) {
            return null;
        }
        return new TodayBar(BigDecimal.valueOf(open), BigDecimal.valueOf(high), BigDecimal.valueOf(low), volume);
    }

    private static Double last(List<Double> values) {
        return values == null || values.isEmpty() ? null : values.getLast();
    }

    /**
     * 여러 종목(최대 {@link #SPARK_MAX_SYMBOLS}개)의 오늘 5분 가격과 전일 종가, 그날 세션 시각을 한 번에 받는다. 장 밖(프리·애프터) 가격도 온다.
     * 응답에 없거나 값이 비정상인 종목은 결과에서 빠진다.
     */
    public List<ExtendedQuote> fetchExtendedQuotes(List<String> symbols) {
        YahooSparkResponse response = restClient
                .get()
                .uri(SPARK_URL, String.join(",", symbols))
                .header(HttpHeaders.USER_AGENT, USER_AGENT)
                .retrieve()
                .body(YahooSparkResponse.class);
        return toExtendedQuotes(response);
    }

    static List<ExtendedQuote> toExtendedQuotes(YahooSparkResponse response) {
        if (response == null || response.spark() == null || response.spark().result() == null) {
            throw new IllegalStateException("야후 여러 종목 응답에 시세가 없습니다");
        }
        List<ExtendedQuote> quotes = new ArrayList<>();
        for (YahooSparkResponse.Result result : response.spark().result()) {
            ExtendedQuote quote = toExtendedQuote(result);
            if (quote != null) {
                quotes.add(quote);
            }
        }
        return quotes;
    }

    // 전일 종가나 세션 시각이 없으면 그 종목은 쓰지 않는다. 가격이 빈 5분 칸(거래 없음)은 뺀다.
    private static ExtendedQuote toExtendedQuote(YahooSparkResponse.Result result) {
        if (result == null
                || result.symbol() == null
                || result.response() == null
                || result.response().isEmpty()) {
            return null;
        }
        YahooSparkResponse.Response body = result.response().getFirst();
        YahooSparkResponse.Meta meta = body.meta();
        if (meta == null || meta.currentTradingPeriod() == null) {
            return null;
        }
        Double previousClose = meta.previousClose() == null ? meta.chartPreviousClose() : meta.previousClose();
        SessionTimes sessions = toSessionTimes(meta.currentTradingPeriod());
        if (previousClose == null || previousClose <= 0 || sessions == null) {
            return null;
        }
        List<PricePoint> prices = new ArrayList<>();
        List<Double> closes = body.indicators() == null
                        || body.indicators().quote() == null
                        || body.indicators().quote().isEmpty()
                ? null
                : body.indicators().quote().getFirst().close();
        if (body.timestamp() != null && closes != null) {
            for (int index = 0; index < body.timestamp().size() && index < closes.size(); index++) {
                Double close = closes.get(index);
                if (close != null && close > 0) {
                    prices.add(new PricePoint(
                            Instant.ofEpochSecond(body.timestamp().get(index)), BigDecimal.valueOf(close)));
                }
            }
        }
        return new ExtendedQuote(result.symbol(), BigDecimal.valueOf(previousClose), sessions, prices);
    }

    private static SessionTimes toSessionTimes(YahooSparkResponse.TradingPeriods periods) {
        if (periods.pre() == null
                || periods.regular() == null
                || periods.pre().start() == null
                || periods.regular().start() == null
                || periods.regular().end() == null
                || periods.post() == null
                || periods.post().end() == null) {
            return null;
        }
        return new SessionTimes(
                Instant.ofEpochSecond(periods.pre().start()),
                Instant.ofEpochSecond(periods.regular().start()),
                Instant.ofEpochSecond(periods.regular().end()),
                Instant.ofEpochSecond(periods.post().end()));
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
        ZoneId zone = exchangeZoneOf(result.meta());
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
                            .atZone(zone)
                            .toLocalDate(),
                    BigDecimal.valueOf(open),
                    BigDecimal.valueOf(high),
                    BigDecimal.valueOf(low),
                    BigDecimal.valueOf(close),
                    volume == null ? 0 : volume));
        }
        return bars;
    }

    /**
     * 일봉 날짜를 읽는 시간대. 응답의 거래소 시간대를 쓰고, 없으면 한국 시간이다.
     * 야후는 보통 일봉을 장 시작 시각(미국 14:30Z, 한국 날짜와 같음)으로 찍지만, 조기 마감일처럼 마지막 체결·마감 시각(18:00Z)으로
     * 찍을 때가 있다. 그 시각은 한국 달력으로 다음 날이라 그날 봉을 못 찾아 종가와 지수가 저장되지 않았다(2026-11-27).
     * 거래소 달력으로 읽으면 두 경우 모두 같은 거래일이다(도쿄는 거래소와 한국 시간이 같다).
     */
    static ZoneId exchangeZoneOf(YahooDailyResponse.Meta meta) {
        if (meta == null || meta.exchangeTimezoneName() == null) {
            return Zone.KST.zoneId();
        }
        try {
            return ZoneId.of(meta.exchangeTimezoneName());
        } catch (java.time.DateTimeException invalid) {
            return Zone.KST.zoneId();
        }
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

    /** today는 응답에 든 그날 일봉(시가·고가·저가·거래량). 없거나 값이 비면 null이다. */
    public record Quote(BigDecimal currentPrice, BigDecimal previousClose, Instant marketTime, TodayBar today) {
        public Quote(BigDecimal currentPrice, BigDecimal previousClose, Instant marketTime) {
            this(currentPrice, previousClose, marketTime, null);
        }
    }

    public record TodayBar(BigDecimal open, BigDecimal high, BigDecimal low, long volume) {}

    public record DailyBar(
            LocalDate date, BigDecimal open, BigDecimal high, BigDecimal low, BigDecimal close, long volume) {}

    public record MarketValue(LocalDate date, BigDecimal value) {}

    public record IndexSession(LocalDate latestBarDate, Instant regularEnd) {}

    /** 한 종목의 오늘 5분 가격(장 밖 포함, 시각 순)과 전일 종가, 세션 시각. */
    public record ExtendedQuote(
            String symbol, BigDecimal previousClose, SessionTimes sessions, List<PricePoint> prices) {}

    /** 그날 프리마켓 시작, 정규장 시작·종료, 애프터마켓 종료 시각. */
    public record SessionTimes(Instant preStart, Instant regularStart, Instant regularEnd, Instant postEnd) {}

    /** 5분 칸의 시작 시각과 그 칸의 마지막 가격. */
    public record PricePoint(Instant time, BigDecimal price) {}
}
