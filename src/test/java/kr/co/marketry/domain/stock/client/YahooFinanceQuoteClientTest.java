package kr.co.marketry.domain.stock.client;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import kr.co.marketry.domain.stock.dto.YahooChartResponse;
import kr.co.marketry.domain.stock.dto.YahooDailyResponse;
import kr.co.marketry.domain.stock.dto.YahooSessionResponse;
import kr.co.marketry.domain.stock.dto.YahooSparkResponse;
import org.junit.jupiter.api.Test;

class YahooFinanceQuoteClientTest {

    @Test
    void 응답의_가격과_전일_종가와_체결_시각을_읽는다() {
        YahooFinanceQuoteClient.Quote quote = YahooFinanceQuoteClient.toQuote(
                "7203", response(new YahooChartResponse.Meta(3338.0, 3533.0, 1793946600L)));

        assertThat(quote.currentPrice()).isEqualByComparingTo("3338");
        assertThat(quote.previousClose()).isEqualByComparingTo("3533");
        assertThat(quote.marketTime()).isEqualTo(Instant.ofEpochSecond(1793946600L));
    }

    @Test
    void 시세가_없거나_0이면_예외를_던진다() {
        assertThatThrownBy(() -> YahooFinanceQuoteClient.toQuote("7203", null))
                .isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> YahooFinanceQuoteClient.toQuote(
                        "7203", new YahooChartResponse(new YahooChartResponse.Chart(List.of()))))
                .isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> YahooFinanceQuoteClient.toQuote(
                        "7203", response(new YahooChartResponse.Meta(null, 3533.0, 1793946600L))))
                .isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> YahooFinanceQuoteClient.toQuote(
                        "7203", response(new YahooChartResponse.Meta(0.0, 3533.0, 1793946600L))))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void 응답의_오늘_일봉을_시가_고가_저가_거래량으로_준다() {
        var bar = new YahooChartResponse.Bar(List.of(7209.0), List.of(7895.0), List.of(7207.0), List.of(33225600L));
        var response = new YahooChartResponse(new YahooChartResponse.Chart(List.of(new YahooChartResponse.Result(
                new YahooChartResponse.Meta(7749.0, 6895.0, 1795653266L),
                new YahooChartResponse.Indicators(List.of(bar))))));

        var quote = YahooFinanceQuoteClient.toQuote("285A", response);

        assertThat(quote.today())
                .isEqualTo(new YahooFinanceQuoteClient.TodayBar(
                        new java.math.BigDecimal("7209.0"),
                        new java.math.BigDecimal("7895.0"),
                        new java.math.BigDecimal("7207.0"),
                        33225600L));
        // 일봉 값이 비면 시세만 쓴다.
        var empty = new YahooChartResponse.Bar(
                java.util.Arrays.asList((Double) null), List.of(7895.0), List.of(7207.0), List.of(1L));
        assertThat(YahooFinanceQuoteClient.toQuote(
                                "285A",
                                new YahooChartResponse(
                                        new YahooChartResponse.Chart(List.of(new YahooChartResponse.Result(
                                                new YahooChartResponse.Meta(7749.0, 6895.0, 1795653266L),
                                                new YahooChartResponse.Indicators(List.of(empty)))))))
                        .today())
                .isNull();
    }

    private static YahooChartResponse response(YahooChartResponse.Meta meta) {
        return new YahooChartResponse(new YahooChartResponse.Chart(List.of(new YahooChartResponse.Result(meta, null))));
    }

    @Test
    void 시가총액은_가장_최근_값을_쓴다() {
        var body = java.util.Map.<String, Object>of(
                "timeseries",
                java.util.Map.of(
                        "result",
                        java.util.List.of(java.util.Map.of(
                                "trailingMarketCap",
                                java.util.List.of(
                                        java.util.Map.of(
                                                "asOfDate",
                                                "2026-07-31",
                                                "reportedValue",
                                                java.util.Map.of("raw", 100)),
                                        java.util.Map.of(
                                                "asOfDate",
                                                "2026-11-06",
                                                "reportedValue",
                                                java.util.Map.of("raw", 200)))))));

        var value = YahooFinanceQuoteClient.toMarketValue("7203", body);

        org.assertj.core.api.Assertions.assertThat(value.date()).isEqualTo(java.time.LocalDate.of(2026, 11, 6));
        org.assertj.core.api.Assertions.assertThat(value.value()).isEqualByComparingTo("200");
    }

    @Test
    void 지수의_최신_봉_날짜와_정규장_종료_시각을_읽는다() {
        // 2026-11-12 09:30 ET 봉(14:30Z), 정규장 종료 16:00 ET(21:00Z)
        long barTime = Instant.parse("2026-11-12T14:30:00Z").getEpochSecond();
        long end = Instant.parse("2026-11-12T21:00:00Z").getEpochSecond();

        YahooFinanceQuoteClient.IndexSession session =
                YahooFinanceQuoteClient.toIndexSession("^GSPC", sessionResponse(List.of(barTime), end));

        assertThat(session.latestBarDate()).isEqualTo(LocalDate.of(2026, 11, 12));
        assertThat(session.regularEnd()).isEqualTo(Instant.ofEpochSecond(end));
    }

    @Test
    void 미국_정규장_시작_봉의_날짜는_미국_거래일과_같다() {
        // 겨울(09:30 EST = 23:30 KST)과 여름(09:30 EDT = 22:30 KST) 모두 한국 달력 날짜가 같다.
        long winter = Instant.parse("2026-11-12T14:30:00Z").getEpochSecond();
        long summer = Instant.parse("2026-07-15T13:30:00Z").getEpochSecond();

        assertThat(YahooFinanceQuoteClient.toIndexSession("^GSPC", sessionResponse(List.of(winter), winter))
                        .latestBarDate())
                .isEqualTo(LocalDate.of(2026, 11, 12));
        assertThat(YahooFinanceQuoteClient.toIndexSession("^GSPC", sessionResponse(List.of(summer), summer))
                        .latestBarDate())
                .isEqualTo(LocalDate.of(2026, 7, 15));
    }

    @Test
    void 장_시간이_없는_응답은_예외를_던진다() {
        assertThatThrownBy(() -> YahooFinanceQuoteClient.toIndexSession("^GSPC", null))
                .isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> YahooFinanceQuoteClient.toIndexSession("^GSPC", sessionResponse(List.of(), 1L)))
                .isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> YahooFinanceQuoteClient.toIndexSession(
                        "^GSPC",
                        new YahooSessionResponse(new YahooSessionResponse.Chart(
                                List.of(new YahooSessionResponse.Result(null, List.of(1L)))))))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void 시가_고가_저가가_비어_있어도_종가가_있으면_종가로_채워_살린다() {
        long first = Instant.parse("2026-11-11T14:30:00Z").getEpochSecond();
        long second = Instant.parse("2026-11-12T14:30:00Z").getEpochSecond();
        YahooDailyResponse response =
                new YahooDailyResponse(new YahooDailyResponse.Chart(List.of(new YahooDailyResponse.Result(
                        List.of(first, second),
                        new YahooDailyResponse.Indicators(List.of(new YahooDailyResponse.Quote(
                                java.util.Arrays.asList(100.0, null),
                                java.util.Arrays.asList(101.0, 111.0),
                                java.util.Arrays.asList(99.0, 109.0),
                                java.util.Arrays.asList(100.5, 110.0),
                                java.util.Arrays.asList(10L, 20L))))))));

        List<YahooFinanceQuoteClient.DailyBar> bars = YahooFinanceQuoteClient.toDailyBars("AAPL", response);

        assertThat(bars).hasSize(2);
        YahooFinanceQuoteClient.DailyBar filled = bars.get(1);
        assertThat(filled.date()).isEqualTo(LocalDate.of(2026, 11, 12));
        assertThat(filled.close()).isEqualByComparingTo("110.0");
        assertThat(filled.open()).isEqualByComparingTo("110.0");
        assertThat(filled.high()).isEqualByComparingTo("111.0");
        assertThat(filled.low()).isEqualByComparingTo("109.0");
    }

    @Test
    void 종가가_없는_봉은_건너뛴다() {
        long first = Instant.parse("2026-11-11T14:30:00Z").getEpochSecond();
        long second = Instant.parse("2026-11-12T14:30:00Z").getEpochSecond();
        YahooDailyResponse response =
                new YahooDailyResponse(new YahooDailyResponse.Chart(List.of(new YahooDailyResponse.Result(
                        List.of(first, second),
                        new YahooDailyResponse.Indicators(List.of(new YahooDailyResponse.Quote(
                                java.util.Arrays.asList(100.0, 110.0),
                                java.util.Arrays.asList(101.0, 111.0),
                                java.util.Arrays.asList(99.0, 109.0),
                                java.util.Arrays.asList(100.5, null),
                                java.util.Arrays.asList(10L, 20L))))))));

        List<YahooFinanceQuoteClient.DailyBar> bars = YahooFinanceQuoteClient.toDailyBars("^N225", response);

        assertThat(bars).hasSize(1);
        assertThat(bars.getFirst().date()).isEqualTo(LocalDate.of(2026, 11, 11));
    }

    private static YahooSessionResponse sessionResponse(List<Long> timestamps, long regularEnd) {
        return new YahooSessionResponse(new YahooSessionResponse.Chart(List.of(new YahooSessionResponse.Result(
                new YahooSessionResponse.Meta(
                        new YahooSessionResponse.CurrentTradingPeriod(new YahooSessionResponse.Period(regularEnd))),
                timestamps))));
    }

    @Test
    void 여러_종목_응답에서_전일_종가와_세션_시각과_빈칸을_뺀_5분_가격을_읽는다() {
        YahooSparkResponse.TradingPeriods periods = new YahooSparkResponse.TradingPeriods(
                new YahooSparkResponse.Period(1000L, 2000L),
                new YahooSparkResponse.Period(2000L, 3000L),
                new YahooSparkResponse.Period(3000L, 4000L));
        YahooSparkResponse response = new YahooSparkResponse(new YahooSparkResponse.Spark(List.of(
                new YahooSparkResponse.Result(
                        "AAPL",
                        List.of(new YahooSparkResponse.Response(
                                new YahooSparkResponse.Meta(null, 100.0, periods),
                                List.of(1000L, 1300L, 1600L),
                                new YahooSparkResponse.Indicators(List.of(
                                        new YahooSparkResponse.Closes(java.util.Arrays.asList(101.0, null, 102.5))))))),
                // 전일 종가가 없는 종목은 뺀다.
                new YahooSparkResponse.Result(
                        "MSFT",
                        List.of(new YahooSparkResponse.Response(
                                new YahooSparkResponse.Meta(null, null, periods), List.of(), null))))));

        List<YahooFinanceQuoteClient.ExtendedQuote> quotes = YahooFinanceQuoteClient.toExtendedQuotes(response);

        assertThat(quotes).hasSize(1);
        YahooFinanceQuoteClient.ExtendedQuote aapl = quotes.getFirst();
        assertThat(aapl.symbol()).isEqualTo("AAPL");
        assertThat(aapl.previousClose()).isEqualByComparingTo("100");
        assertThat(aapl.sessions().preStart()).isEqualTo(Instant.ofEpochSecond(1000));
        assertThat(aapl.sessions().regularStart()).isEqualTo(Instant.ofEpochSecond(2000));
        assertThat(aapl.prices())
                .extracting(YahooFinanceQuoteClient.PricePoint::time)
                .containsExactly(Instant.ofEpochSecond(1000), Instant.ofEpochSecond(1600));
        assertThat(aapl.prices().getLast().price()).isEqualByComparingTo("102.5");
    }

    @Test
    void 도쿄_묶음_응답은_마지막_5분_가격을_현재가로_칸_끝을_체결_시각으로_쓴다() {
        Instant slotStart = Instant.parse("2026-11-27T06:25:00Z"); // 15:25 KST 칸, 장 마감 칸
        YahooFinanceQuoteClient.ExtendedQuote extended = extended(
                new YahooFinanceQuoteClient.PricePoint(slotStart.minusSeconds(300), new java.math.BigDecimal("3390")),
                new YahooFinanceQuoteClient.PricePoint(slotStart, new java.math.BigDecimal("3397")));

        YahooFinanceQuoteClient.Quote closed =
                YahooFinanceQuoteClient.toTokyoQuote(extended, Instant.parse("2026-11-27T06:58:00Z"));
        YahooFinanceQuoteClient.Quote running =
                YahooFinanceQuoteClient.toTokyoQuote(extended, Instant.parse("2026-11-27T06:27:10Z"));

        assertThat(closed.currentPrice()).isEqualByComparingTo("3397");
        assertThat(closed.previousClose()).isEqualByComparingTo("3312");
        // 마감 칸은 끝(15:30)이 체결 시각이라 15:30 종가 시세로 저장된다.
        assertThat(closed.marketTime()).isEqualTo(Instant.parse("2026-11-27T06:30:00Z"));
        // 아직 진행 중인 칸은 지금 시각이다.
        assertThat(running.marketTime()).isEqualTo(Instant.parse("2026-11-27T06:27:10Z"));
        assertThat(closed.today()).isNull();
    }

    @Test
    void 도쿄_묶음_응답에_가격이_없으면_그_종목은_없는_것으로_본다() {
        assertThat(YahooFinanceQuoteClient.toTokyoQuote(extended(), Instant.parse("2026-11-27T06:58:00Z")))
                .isNull();
    }

    private static YahooFinanceQuoteClient.ExtendedQuote extended(YahooFinanceQuoteClient.PricePoint... points) {
        return new YahooFinanceQuoteClient.ExtendedQuote(
                "7203.T",
                new java.math.BigDecimal("3312"),
                new YahooFinanceQuoteClient.SessionTimes(
                        Instant.parse("2026-11-27T00:00:00Z"),
                        Instant.parse("2026-11-27T00:00:00Z"),
                        Instant.parse("2026-11-27T06:30:00Z")),
                List.of(points));
    }
}
