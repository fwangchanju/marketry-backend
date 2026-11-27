package kr.co.marketry.domain.stock.collector;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.io.ByteArrayInputStream;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import kr.co.marketry.domain.stock.client.YahooFinanceQuoteClient;
import kr.co.marketry.domain.stock.client.YahooFinanceQuoteClient.ExtendedQuote;
import kr.co.marketry.domain.stock.client.YahooFinanceQuoteClient.PricePoint;
import kr.co.marketry.domain.stock.client.YahooFinanceQuoteClient.SessionTimes;
import kr.co.marketry.domain.stock.entity.OverseasStockQuote;
import kr.co.marketry.domain.stock.entity.OverseasStockQuoteId;
import kr.co.marketry.domain.stock.repository.OverseasStockQuoteRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.http.HttpStatus;
import org.springframework.web.client.HttpClientErrorException;

class UsaIntradayCollectorTest {
    // 2026-11-12(EST, UTC-5): 프리마켓 04:00 = 09:00Z, 정규장 09:30 = 14:30Z, 정규장 종료 16:00 = 21:00Z, 애프터마켓 종료 20:00 =
    // 01:00Z(다음 날)
    private static final SessionTimes SESSIONS = new SessionTimes(
            Instant.parse("2026-11-12T09:00:00Z"),
            Instant.parse("2026-11-12T14:30:00Z"),
            Instant.parse("2026-11-12T21:00:00Z"),
            Instant.parse("2026-11-13T01:00:00Z"));
    private static final LocalDateTime LAST_CLOSE = LocalDateTime.of(2026, 11, 11, 16, 0);
    private static final Instant IN_PRE_MARKET = Instant.parse("2026-11-12T13:12:00Z");

    private YahooFinanceQuoteClient client;
    private OverseasStockQuoteRepository repository;

    @BeforeEach
    void setUp() {
        client = mock(YahooFinanceQuoteClient.class);
        repository = mock(OverseasStockQuoteRepository.class);
    }

    @Test
    void 프리마켓_마지막_가격을_뉴욕_시각_5분_단위로_전일_종가_대비_등락률과_함께_저장한다() {
        givenLast(LAST_CLOSE, row("AAPL", "100.00", "1000000"));
        // 08:05 EST 칸(13:05Z) 가격 103.4, 08:10 칸(13:10Z) 105
        givenQuotes(quote("AAPL", "100", point("2026-11-12T13:05:00Z", "103.4"), point("2026-11-12T13:10:00Z", "105")));

        UsaIntradayCollector.Result result = collector(IN_PRE_MARKET, "AAPL").collect();

        assertThat(result.saved()).isTrue();
        assertThat(result.snapshotTime()).isEqualTo(LocalDateTime.of(2026, 11, 12, 8, 10));
        OverseasStockQuote saved = savedRows().getFirst();
        assertThat(saved.getId().getSnapshotTime()).isEqualTo(LocalDateTime.of(2026, 11, 12, 8, 10));
        assertThat(saved.getCurrentPrice()).isEqualByComparingTo("105.00");
        assertThat(saved.getLastPrice()).isEqualByComparingTo("100.00");
        assertThat(saved.getChangeRate()).isEqualByComparingTo("5.00");
        assertThat(saved.getTotalMarketValue()).isEqualByComparingTo("1050000");
    }

    @Test
    void 프리마켓에_아직_거래가_없는_종목은_전일_종가_그대로_0퍼센트로_저장한다() {
        givenLast(LAST_CLOSE, row("AAPL", "100.00", "1000000"), row("MSFT", "200.00", "2000000"));
        // 전날 애프터마켓 가격(프리마켓 시작 전)은 쓰지 않는다.
        givenQuotes(
                quote("AAPL", "100", point("2026-11-12T13:10:00Z", "105")),
                quote("MSFT", "200", point("2026-11-12T00:55:00Z", "190")));

        collector(IN_PRE_MARKET, "AAPL", "MSFT").collect();

        OverseasStockQuote msft = savedRows().stream()
                .filter(row -> row.getId().getStockCode().equals("MSFT"))
                .findFirst()
                .orElseThrow();
        assertThat(msft.getCurrentPrice()).isEqualByComparingTo("200.00");
        assertThat(msft.getChangeRate()).isEqualByComparingTo("0.00");
        assertThat(msft.getTotalMarketValue()).isEqualByComparingTo("2000000");
    }

    @Test
    void 프리마켓_시작_전이나_애프터마켓_종료_뒤에는_저장하지_않는다() {
        givenLast(LAST_CLOSE, row("AAPL", "100.00", "1000000"));
        givenQuotes(quote("AAPL", "100", point("2026-11-12T13:10:00Z", "105")));

        UsaIntradayCollector.Result before =
                collector(Instant.parse("2026-11-12T08:59:00Z"), "AAPL").collect();
        UsaIntradayCollector.Result after =
                collector(Instant.parse("2026-11-13T01:01:00Z"), "AAPL").collect();

        assertThat(before.saved()).isFalse();
        assertThat(after.saved()).isFalse();
        verify(repository, never()).saveAll(anyList());
    }

    @Test
    void 정규장과_애프터마켓에도_가장_최근_5분_가격을_저장한다() {
        givenLast(LAST_CLOSE, row("AAPL", "100.00", "1000000"));
        // 정규장 10:30 EST 칸(15:30Z) 가격 110, 애프터마켓 16:05 칸(21:05Z) 가격 108
        givenQuotes(quote("AAPL", "100", point("2026-11-12T15:30:00Z", "110"), point("2026-11-12T21:05:00Z", "108")));

        UsaIntradayCollector.Result afterHours =
                collector(Instant.parse("2026-11-12T21:08:00Z"), "AAPL").collect();

        assertThat(afterHours.saved()).isTrue();
        assertThat(afterHours.snapshotTime()).isEqualTo(LocalDateTime.of(2026, 11, 12, 16, 5));
        OverseasStockQuote saved = savedRows().getFirst();
        assertThat(saved.getCurrentPrice()).isEqualByComparingTo("108.00");
        assertThat(saved.getChangeRate()).isEqualByComparingTo("8.00");
    }

    @Test
    void 애프터마켓_첫_칸이_16시이면_종가_줄을_덮어쓰지_않도록_저장하지_않는다() {
        givenLast(LocalDateTime.of(2026, 11, 12, 15, 55), row("AAPL", "110.00", "1100000"));
        givenQuotes(quote("AAPL", "100", point("2026-11-12T20:55:00Z", "110"), point("2026-11-12T21:00:00Z", "109")));

        UsaIntradayCollector.Result result =
                collector(Instant.parse("2026-11-12T21:02:00Z"), "AAPL").collect();

        assertThat(result.saved()).isFalse();
        assertThat(result.reason()).startsWith("16:00 줄은 종가 수집이");
        verify(repository, never()).saveAll(anyList());
    }

    @Test
    void 이미_저장한_시각보다_새_시세가_아니면_저장하지_않는다() {
        givenLast(LocalDateTime.of(2026, 11, 12, 8, 10), row("AAPL", "105.00", "1050000"));
        givenQuotes(quote("AAPL", "100", point("2026-11-12T13:10:00Z", "105")));

        UsaIntradayCollector.Result result = collector(IN_PRE_MARKET, "AAPL").collect();

        assertThat(result.saved()).isFalse();
        assertThat(result.reason()).isEqualTo("새 시세가 없습니다");
    }

    @Test
    void 받은_종목이_90퍼센트_미만이면_저장하지_않는다() {
        givenLast(LAST_CLOSE, row("AAPL", "100.00", "1000000"), row("MSFT", "200.00", "2000000"));
        givenQuotes(quote("AAPL", "100", point("2026-11-12T13:10:00Z", "105")));

        UsaIntradayCollector.Result result =
                collector(IN_PRE_MARKET, "AAPL", "MSFT").collect();

        assertThat(result.saved()).isFalse();
        verify(repository, never()).saveAll(anyList());
    }

    @Test
    void 장_시간_밖과_새_시세_없음은_실패로_세지_않고_응답_부족과_호출_제한은_실패로_센다() {
        givenLast(LAST_CLOSE, row("AAPL", "100.00", "1000000"), row("MSFT", "200.00", "2000000"));
        givenQuotes(quote("AAPL", "100", point("2026-11-12T13:10:00Z", "105")));

        UsaIntradayCollector.Result tooFew =
                collector(IN_PRE_MARKET, "AAPL", "MSFT").collect();
        UsaIntradayCollector.Result outside =
                collector(Instant.parse("2026-11-12T08:59:00Z"), "AAPL").collect();

        assertThat(tooFew.fetchFailed()).isTrue();
        assertThat(outside.fetchFailed()).isFalse();
    }

    @Test
    void 종목을_20개씩_묶어_부르고_야후가_호출을_제한하면_나머지_묶음은_부르지_않는다() {
        List<String> symbols = new ArrayList<>();
        for (int index = 0; index < 45; index++) {
            symbols.add("S" + index);
        }
        when(client.fetchExtendedQuotes(anyList()))
                .thenThrow(HttpClientErrorException.create(HttpStatus.TOO_MANY_REQUESTS, "limit", null, null, null));

        UsaIntradayCollector.Result result =
                collector(IN_PRE_MARKET, symbols.toArray(String[]::new)).collect();

        assertThat(result.saved()).isFalse();
        assertThat(result.reason()).startsWith("야후 호출 제한");
        verify(client, times(1)).fetchExtendedQuotes(anyList());
    }

    @Test
    void 묶음은_최대_20종목이다() {
        List<String> symbols = new ArrayList<>();
        for (int index = 0; index < 45; index++) {
            symbols.add("S" + index);
        }
        when(client.fetchExtendedQuotes(anyList())).thenReturn(List.of());

        collector(IN_PRE_MARKET, symbols.toArray(String[]::new)).collect();

        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<String>> captor = ArgumentCaptor.forClass(List.class);
        verify(client, times(3)).fetchExtendedQuotes(captor.capture());
        assertThat(captor.getAllValues()).extracting(List::size).containsExactly(20, 20, 5);
    }

    private UsaIntradayCollector collector(Instant now, String... symbols) {
        StringBuilder csv = new StringBuilder("symbol,name,date_added\n");
        for (String symbol : symbols) {
            csv.append(symbol).append(",").append(symbol).append(" Inc,2020-01-01\n");
        }
        UsaConstituents constituents =
                new UsaConstituents(new ByteArrayInputStream(csv.toString().getBytes(StandardCharsets.UTF_8)));
        return new UsaIntradayCollector(
                client, repository, constituents, duration -> {}, Clock.fixed(now, ZoneOffset.UTC));
    }

    private void givenLast(LocalDateTime time, OverseasStockQuote... rows) {
        List<OverseasStockQuote> all = new ArrayList<>();
        for (OverseasStockQuote row : rows) {
            all.add(OverseasStockQuote.create(
                    new OverseasStockQuoteId("USA", row.getId().getStockCode(), time),
                    row.getCurrentPrice(),
                    row.getLastPrice(),
                    row.getChangeRate(),
                    row.getTotalMarketValue()));
        }
        when(repository.findFirstByIdMarketOrderByIdSnapshotTimeDesc("USA")).thenReturn(Optional.of(all.getFirst()));
        when(repository.findAllByIdMarketAndIdSnapshotTime("USA", time)).thenReturn(all);
    }

    private void givenQuotes(ExtendedQuote... quotes) {
        when(client.fetchExtendedQuotes(anyList())).thenReturn(List.of(quotes));
    }

    @SuppressWarnings("unchecked")
    private List<OverseasStockQuote> savedRows() {
        ArgumentCaptor<List<OverseasStockQuote>> captor = ArgumentCaptor.forClass(List.class);
        verify(repository).saveAll(captor.capture());
        return captor.getValue();
    }

    private static OverseasStockQuote row(String code, String price, String marketValue) {
        return OverseasStockQuote.create(
                new OverseasStockQuoteId("USA", code, LAST_CLOSE),
                new BigDecimal(price),
                new BigDecimal(price),
                BigDecimal.ZERO,
                new BigDecimal(marketValue));
    }

    private static ExtendedQuote quote(String symbol, String previousClose, PricePoint... points) {
        return new ExtendedQuote(symbol, new BigDecimal(previousClose), SESSIONS, List.of(points));
    }

    private static PricePoint point(String time, String price) {
        return new PricePoint(Instant.parse(time), new BigDecimal(price));
    }
}
