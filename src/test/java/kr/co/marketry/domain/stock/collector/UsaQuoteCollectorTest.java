package kr.co.marketry.domain.stock.collector;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.io.ByteArrayInputStream;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import kr.co.marketry.domain.stock.client.YahooFinanceQuoteClient;
import kr.co.marketry.domain.stock.client.YahooFinanceQuoteClient.DailyBar;
import kr.co.marketry.domain.stock.client.YahooFinanceQuoteClient.IndexSession;
import kr.co.marketry.domain.stock.client.YahooFinanceQuoteClient.MarketValue;
import kr.co.marketry.domain.stock.entity.OverseasStockQuote;
import kr.co.marketry.domain.stock.entity.OverseasStockQuoteId;
import kr.co.marketry.domain.stock.repository.OverseasStockQuoteRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.http.HttpStatus;
import org.springframework.web.client.HttpClientErrorException;

class UsaQuoteCollectorTest {
    private static final LocalDate DAY = LocalDate.of(2026, 11, 12);
    // 2026-11-12 16:00 EST = 21:00Z
    private static final Instant CLOSE = Instant.parse("2026-11-12T21:00:00Z");
    private static final Instant AFTER_DELAY = Instant.parse("2026-11-12T21:30:00Z");
    private static final LocalDateTime LAST_TIME = LocalDateTime.of(2026, 11, 11, 16, 0);

    private YahooFinanceQuoteClient client;
    private OverseasStockQuoteRepository repository;

    @BeforeEach
    void setUp() {
        client = mock(YahooFinanceQuoteClient.class);
        repository = mock(OverseasStockQuoteRepository.class);
        when(client.fetchIndexSession("^GSPC")).thenReturn(new IndexSession(DAY, CLOSE));
    }

    @Test
    void 마감_후_대기_시간이_지나면_종가와_등락률과_시가총액을_저장한다() {
        givenLast(LAST_TIME, lastRow("AAPL", "100.00", "1000000"));
        givenBars("AAPL", bar("2026-11-11", "100"), bar("2026-11-12", "110"));

        UsaQuoteCollector.Result result = collector(AFTER_DELAY, "AAPL").collect();

        assertThat(result.saved()).isTrue();
        assertThat(result.snapshotTime()).isEqualTo(LocalDateTime.of(2026, 11, 12, 16, 0));
        OverseasStockQuote saved = savedRows().getFirst();
        assertThat(saved.getId().getMarket()).isEqualTo("USA");
        assertThat(saved.getCurrentPrice()).isEqualByComparingTo("110.00");
        assertThat(saved.getLastPrice()).isEqualByComparingTo("100.00");
        assertThat(saved.getChangeRate()).isEqualByComparingTo("10.00");
        assertThat(saved.getTotalMarketValue()).isEqualByComparingTo("1100000");
    }

    @Test
    void 마감_후_대기_시간이_지나기_전에는_저장하지_않는다() {
        givenLast(LAST_TIME, lastRow("AAPL", "100.00", "1000000"));

        UsaQuoteCollector.Result result =
                collector(AFTER_DELAY.minusSeconds(1), "AAPL").collect();

        assertThat(result.status()).isEqualTo(UsaQuoteCollector.Status.WAITING);
        assertThat(result.fetchFailed()).isFalse();
        verify(repository, never()).saveAll(any());
    }

    @Test
    void 장_종료_시각의_날짜가_최신_봉_날짜와_다르면_저장하지_않는다() {
        // 마감 뒤 야후가 다음 거래일의 종료 시각(11-13 16:00 EST)으로 넘긴 경우
        when(client.fetchIndexSession("^GSPC"))
                .thenReturn(new IndexSession(DAY, Instant.parse("2026-11-13T21:00:00Z")));
        givenLast(LAST_TIME, lastRow("AAPL", "100.00", "1000000"));

        UsaQuoteCollector.Result result =
                collector(Instant.parse("2026-11-13T21:31:00Z"), "AAPL").collect();

        assertThat(result.status()).isEqualTo(UsaQuoteCollector.Status.WAITING);
        verify(repository, never()).saveAll(any());
    }

    @Test
    void 그_날짜를_이미_저장했으면_다시_저장하지_않는다() {
        givenLast(DAY.atTime(16, 0), lastRow("AAPL", "110.00", "1100000"));

        UsaQuoteCollector.Result result = collector(AFTER_DELAY, "AAPL").collect();

        assertThat(result.status()).isEqualTo(UsaQuoteCollector.Status.ALREADY_SAVED);
        verify(repository, never()).saveAll(any());
        verify(client, never()).fetchDailyBars(anyString(), any(), any());
    }

    @Test
    void 기준_시세가_없으면_저장하지_않는다() {
        when(repository.findFirstByIdMarketOrderByIdSnapshotTimeDesc("USA")).thenReturn(Optional.empty());

        UsaQuoteCollector.Result result = collector(AFTER_DELAY, "AAPL").collect();

        assertThat(result.status()).isEqualTo(UsaQuoteCollector.Status.NO_BASELINE);
        verify(repository, never()).saveAll(any());
    }

    @Test
    void 일부_종목을_받지_못해도_90퍼센트_이상이면_받은_종목만_저장하고_빠진_종목은_채우지_않는다() {
        String[] symbols = {"A", "B", "C", "D", "E", "F", "G", "H", "I", "J"};
        givenLast(LAST_TIME, rows(symbols));
        for (int index = 0; index < 9; index++) {
            givenBars(symbols[index], bar("2026-11-11", "100"), bar("2026-11-12", "101"));
        }
        // J는 오늘 봉이 없다.
        givenBars("J", bar("2026-11-11", "100"));

        UsaQuoteCollector.Result result = collector(AFTER_DELAY, symbols).collect();

        assertThat(result.saved()).isTrue();
        assertThat(result.failureCount()).isEqualTo(1);
        assertThat(savedRows()).hasSize(9);
        assertThat(savedRows()).noneMatch(row -> row.getId().getStockCode().equals("J"));
    }

    @Test
    void 받은_종목이_90퍼센트_미만이면_아무것도_저장하지_않는다() {
        String[] symbols = {"A", "B", "C", "D", "E", "F", "G", "H", "I", "J"};
        givenLast(LAST_TIME, rows(symbols));
        for (int index = 0; index < 8; index++) {
            givenBars(symbols[index], bar("2026-11-11", "100"), bar("2026-11-12", "101"));
        }
        givenBars("I", bar("2026-11-11", "100"));
        givenBars("J", bar("2026-11-11", "100"));

        UsaQuoteCollector.Result result = collector(AFTER_DELAY, symbols).collect();

        assertThat(result.status()).isEqualTo(UsaQuoteCollector.Status.TOO_FEW);
        assertThat(result.fetchFailed()).isTrue();
        verify(repository, never()).saveAll(any());
    }

    @Test
    void 야후가_호출을_제한하면_나머지를_포기하고_저장하지_않는다() {
        String[] symbols = {"A", "B", "C"};
        givenLast(LAST_TIME, rows(symbols));
        givenBars("A", bar("2026-11-11", "100"), bar("2026-11-12", "101"));
        when(client.fetchDailyBars(eq("B"), any(), any()))
                .thenThrow(new HttpClientErrorException(HttpStatus.TOO_MANY_REQUESTS));

        UsaQuoteCollector.Result result = collector(AFTER_DELAY, symbols).collect();

        assertThat(result.fetchFailed()).isTrue();
        assertThat(result.reason()).isEqualTo("야후 호출 제한");
        verify(client, never()).fetchDailyBars(eq("C"), any(), any());
        verify(repository, never()).saveAll(any());
    }

    @Test
    void 직전_저장이_며칠_전이어도_그_날짜_종가_대비로_시가총액을_잇고_전일_종가는_바로_앞_거래일을_쓴다() {
        LocalDateTime mondayClose = LocalDateTime.of(2026, 11, 9, 16, 0);
        givenLast(mondayClose, lastRow("AAPL", "100.00", "1000000"));
        givenBars(
                "AAPL",
                bar("2026-11-09", "100"),
                bar("2026-11-10", "105"),
                bar("2026-11-11", "108"),
                bar("2026-11-12", "110"));

        UsaQuoteCollector.Result result = collector(AFTER_DELAY, "AAPL").collect();

        assertThat(result.saved()).isTrue();
        OverseasStockQuote saved = savedRows().getFirst();
        assertThat(saved.getLastPrice()).isEqualByComparingTo("108.00");
        assertThat(saved.getChangeRate()).isEqualByComparingTo("1.85");
        assertThat(saved.getTotalMarketValue()).isEqualByComparingTo("1100000");
    }

    @Test
    void 분할일에는_저장된_옛_가격이_아니라_같은_응답의_분할_반영_종가로_시가총액을_계산한다() {
        // 저장된 직전 종가는 분할 전 400이고, 야후 일봉은 분할 반영 값(100)으로 온다.
        givenLast(LAST_TIME, lastRow("AAPL", "400.00", "3200000"));
        givenBars("AAPL", bar("2026-11-11", "100"), bar("2026-11-12", "101"));

        UsaQuoteCollector.Result result = collector(AFTER_DELAY, "AAPL").collect();

        assertThat(result.saved()).isTrue();
        OverseasStockQuote saved = savedRows().getFirst();
        assertThat(saved.getChangeRate()).isEqualByComparingTo("1.00");
        assertThat(saved.getTotalMarketValue()).isEqualByComparingTo("3232000");
    }

    @Test
    void 직전_스냅샷에_없던_종목은_야후_시가총액_날짜의_종가_대비로_환산한다() {
        givenLast(LAST_TIME, lastRow("AAPL", "100.00", "1000000"));
        givenBars("AAPL", bar("2026-11-11", "100"), bar("2026-11-12", "110"));
        givenBars("NEW", bar("2026-11-10", "50"), bar("2026-11-11", "52"), bar("2026-11-12", "55"));
        when(client.fetchMarketValueOf(eq("NEW"), any(), any()))
                .thenReturn(new MarketValue(LocalDate.of(2026, 11, 10), new BigDecimal("1000000")));

        UsaQuoteCollector.Result result = collector(AFTER_DELAY, "AAPL", "NEW").collect();

        assertThat(result.saved()).isTrue();
        OverseasStockQuote added = savedRows().stream()
                .filter(row -> row.getId().getStockCode().equals("NEW"))
                .findFirst()
                .orElseThrow();
        assertThat(added.getTotalMarketValue()).isEqualByComparingTo("1100000");
    }

    private UsaQuoteCollector collector(Instant now, String... symbols) {
        StringBuilder csv = new StringBuilder("symbol,name,date_added\n");
        for (String symbol : symbols) {
            csv.append(symbol).append(",").append(symbol).append(" Inc,2020-01-01\n");
        }
        UsaConstituents constituents =
                new UsaConstituents(new ByteArrayInputStream(csv.toString().getBytes(StandardCharsets.UTF_8)));
        return new UsaQuoteCollector(
                client,
                repository,
                constituents,
                Duration.ofMinutes(30),
                duration -> {},
                Clock.fixed(now, ZoneOffset.UTC));
    }

    private void givenLast(LocalDateTime time, OverseasStockQuote... rows) {
        OverseasStockQuote first = rows[0];
        OverseasStockQuote latest = OverseasStockQuote.create(
                new OverseasStockQuoteId("USA", first.getId().getStockCode(), time),
                first.getCurrentPrice(),
                first.getLastPrice(),
                first.getChangeRate(),
                first.getTotalMarketValue());
        when(repository.findFirstByIdMarketOrderByIdSnapshotTimeDesc("USA")).thenReturn(Optional.of(latest));
        List<OverseasStockQuote> all = java.util.Arrays.stream(rows)
                .map(row -> OverseasStockQuote.create(
                        new OverseasStockQuoteId("USA", row.getId().getStockCode(), time),
                        row.getCurrentPrice(),
                        row.getLastPrice(),
                        row.getChangeRate(),
                        row.getTotalMarketValue()))
                .toList();
        when(repository.findAllByIdMarketAndIdSnapshotTime("USA", time)).thenReturn(all);
    }

    private void givenBars(String symbol, DailyBar... bars) {
        when(client.fetchDailyBars(eq(symbol), any(), any())).thenReturn(List.of(bars));
    }

    private List<OverseasStockQuote> savedRows() {
        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<OverseasStockQuote>> captor = ArgumentCaptor.forClass(List.class);
        verify(repository).saveAll(captor.capture());
        return captor.getValue();
    }

    private static OverseasStockQuote[] rows(String... symbols) {
        OverseasStockQuote[] rows = new OverseasStockQuote[symbols.length];
        for (int index = 0; index < symbols.length; index++) {
            rows[index] = lastRow(symbols[index], "100.00", "1000000");
        }
        return rows;
    }

    private static OverseasStockQuote lastRow(String code, String price, String marketValue) {
        return OverseasStockQuote.create(
                new OverseasStockQuoteId("USA", code, LAST_TIME),
                new BigDecimal(price),
                new BigDecimal(price),
                BigDecimal.ZERO,
                new BigDecimal(marketValue));
    }

    private static DailyBar bar(String date, String close) {
        BigDecimal value = new BigDecimal(close);
        return new DailyBar(LocalDate.parse(date), value, value, value, value, 1000L);
    }
}
