package kr.co.marketry.domain.stock.collector;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import kr.co.marketry.domain.stock.client.YahooFinanceQuoteClient;
import kr.co.marketry.domain.stock.entity.OverseasIndexQuote;
import kr.co.marketry.domain.stock.entity.OverseasStockDaily;
import kr.co.marketry.domain.stock.entity.OverseasStockQuote;
import kr.co.marketry.domain.stock.entity.OverseasStockQuoteId;
import kr.co.marketry.domain.stock.repository.OverseasIndexQuoteRepository;
import kr.co.marketry.domain.stock.repository.OverseasStockDailyRepository;
import kr.co.marketry.domain.stock.repository.OverseasStockQuoteRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.http.HttpStatus;
import org.springframework.web.client.HttpClientErrorException;

class JapanQuoteCollectorTest {
    private static final LocalDateTime LAST_TIME = LocalDateTime.of(2026, 11, 6, 15, 30);
    private static final LocalDate COLLECTION_DATE = LocalDate.of(2026, 11, 9);
    // 2026-11-09 10:07:30 KST
    private static final Instant MARKET_TIME = Instant.parse("2026-11-09T01:07:30Z");
    // 2026-11-09 09:00:00 KST
    private static final Instant OPENING_TIME = Instant.parse("2026-11-09T00:00:00Z");

    private YahooFinanceQuoteClient client;
    private OverseasStockQuoteRepository repository;
    private OverseasIndexQuoteRepository indexQuotes;
    private OverseasStockDailyRepository dailies;
    private JapanQuoteCollector collector;

    @BeforeEach
    void setUp() {
        client = mock(YahooFinanceQuoteClient.class);
        repository = mock(OverseasStockQuoteRepository.class);
        indexQuotes = mock(OverseasIndexQuoteRepository.class);
        dailies = mock(OverseasStockDailyRepository.class);
        collector = new JapanQuoteCollector(client, repository, indexQuotes, dailies, duration -> {});
    }

    @Test
    void 받은_시세를_체결_시각의_5분_단위로_저장하고_시가총액을_가격만큼_조정한다() {
        givenLastQuotes(lastQuote("7203", "1000.00", "1000000"));
        when(client.fetchTokyoQuote("7203")).thenReturn(quote("1100", "1000", MARKET_TIME));

        JapanQuoteCollector.Result result = collector.collect(COLLECTION_DATE);

        assertThat(result.saved()).isTrue();
        assertThat(result.snapshotTime()).isEqualTo(LocalDateTime.of(2026, 11, 9, 10, 5));
        OverseasStockQuote saved = savedQuotes().getFirst();
        assertThat(saved.getId().getSnapshotTime()).isEqualTo(LocalDateTime.of(2026, 11, 9, 10, 5));
        assertThat(saved.getCurrentPrice()).isEqualByComparingTo("1100");
        assertThat(saved.getLastPrice()).isEqualByComparingTo("1000");
        assertThat(saved.getChangeRate()).isEqualByComparingTo("10.00");
        assertThat(saved.getTotalMarketValue()).isEqualByComparingTo("1100000");
    }

    @Test
    void 체결_시각이_마지막_시세보다_늦지_않으면_저장하지_않는다() {
        givenLastQuotes(lastQuote("7203", "1000.00", "1000000"));
        // 2026-11-06 15:30:02 KST — 휴장일에는 직전 거래일 시각이 그대로 온다.
        when(client.fetchTokyoQuote("7203")).thenReturn(quote("1000", "990", Instant.parse("2026-11-06T06:30:02Z")));

        JapanQuoteCollector.Result result = collector.collect(COLLECTION_DATE);

        assertThat(result.saved()).isFalse();
        assertThat(result.todayQuoteCount()).isZero();
        assertThat(result.otherDateQuoteCount()).isEqualTo(1);
        verify(repository, never()).saveAll(any());
    }

    @Test
    void 일부_종목을_받지_못하면_그_종목은_마지막_값을_유지한다() {
        List<OverseasStockQuote> last = new ArrayList<>();
        for (int index = 0; index < 10; index++) {
            last.add(lastQuote("C" + index, "1000.00", "1000000"));
        }
        givenLastQuotes(last.toArray(OverseasStockQuote[]::new));
        when(client.fetchTokyoQuote(anyString())).thenReturn(quote("1100", "1000", MARKET_TIME));
        when(client.fetchTokyoQuote("C3")).thenThrow(new IllegalStateException("없음"));

        JapanQuoteCollector.Result result = collector.collect(COLLECTION_DATE);

        assertThat(result.saved()).isTrue();
        assertThat(result.successCount()).isEqualTo(9);
        assertThat(result.todayQuoteCount()).isEqualTo(9);
        assertThat(result.otherDateQuoteCount()).isZero();
        assertThat(result.failureCount()).isEqualTo(1);
        OverseasStockQuote kept = savedQuotes().stream()
                .filter(quote -> quote.getId().getStockCode().equals("C3"))
                .findFirst()
                .orElseThrow();
        assertThat(kept.getCurrentPrice()).isEqualByComparingTo("1000.00");
        assertThat(kept.getTotalMarketValue()).isEqualByComparingTo("1000000");
        assertThat(kept.getId().getSnapshotTime()).isEqualTo(LocalDateTime.of(2026, 11, 9, 10, 5));
    }

    @Test
    void 받은_종목이_90퍼센트보다_적으면_저장하지_않는다() {
        givenLastQuotes(lastQuote("A", "1000.00", "1"), lastQuote("B", "1000.00", "1"));
        when(client.fetchTokyoQuote("A")).thenReturn(quote("1100", "1000", MARKET_TIME));
        when(client.fetchTokyoQuote("B")).thenThrow(new IllegalStateException("없음"));

        JapanQuoteCollector.Result result = collector.collect(COLLECTION_DATE);

        assertThat(result.saved()).isFalse();
        verify(repository, never()).saveAll(any());
    }

    @Test
    void 호출_제한을_받으면_남은_종목을_요청하지_않고_저장하지_않는다() {
        givenLastQuotes(lastQuote("A", "1000.00", "1"), lastQuote("B", "1000.00", "1"), lastQuote("C", "1000.00", "1"));
        when(client.fetchTokyoQuote("A"))
                .thenThrow(HttpClientErrorException.create(HttpStatus.TOO_MANY_REQUESTS, "", null, null, null));

        JapanQuoteCollector.Result result = collector.collect(COLLECTION_DATE);

        assertThat(result.saved()).isFalse();
        verify(client, times(1)).fetchTokyoQuote(anyString());
        verify(repository, never()).saveAll(any());
    }

    @Test
    void 기준_시세가_없으면_요청하지_않는다() {
        when(repository.findFirstByIdMarketOrderByIdSnapshotTimeDesc("JAPAN")).thenReturn(Optional.empty());

        JapanQuoteCollector.Result result = collector.collect(COLLECTION_DATE);

        assertThat(result.saved()).isFalse();
        verify(client, never()).fetchTokyoQuote(anyString());
    }

    @Test
    void 등락률은_전일_종가_기준_소수_둘째_자리로_반올림한다() {
        assertThat(JapanQuoteCollector.changeRateOf(new BigDecimal("1022.00"), new BigDecimal("1131.50")))
                .isEqualByComparingTo("-9.68");
    }

    @Test
    void 전일_시각_응답은_오늘_시세_집계에서_분리한다() {
        givenTodayQuotesWithHalted(9);
        when(client.fetchTokyoQuote("2914")).thenReturn(quote("6269", "6269", Instant.parse("2026-11-06T06:30:00Z")));

        JapanQuoteCollector.Result result = collector.collect(COLLECTION_DATE);

        assertThat(result.saved()).isTrue();
        assertThat(result.todayQuoteCount()).isEqualTo(9);
        assertThat(result.otherDateQuoteCount()).isEqualTo(1);
        assertThat(result.failureCount()).isZero();
        OverseasStockQuote savedHaltedStock = savedQuotes().stream()
                .filter(quote -> quote.getId().getStockCode().equals("2914"))
                .findFirst()
                .orElseThrow();
        assertThat(savedHaltedStock.getId().getSnapshotTime()).isEqualTo(result.snapshotTime());
    }

    @Test
    void 오늘_시세가_90퍼센트보다_적으면_어제_값으로_채워_저장하지_않고_기다린다() {
        // 개장 직후: 10종목 중 2종목만 오늘 시세가 왔고 나머지는 어제 종가 시각 그대로다.
        java.util.List<OverseasStockQuote> last = new java.util.ArrayList<>();
        for (int index = 0; index < 10; index++) last.add(lastQuote("10" + index + "0", "1000.00", "1000000"));
        givenLastQuotes(last.toArray(OverseasStockQuote[]::new));
        for (int index = 0; index < 10; index++) {
            when(client.fetchTokyoQuote("10" + index + "0"))
                    .thenReturn(
                            index < 2
                                    ? quote("1100", "1000", MARKET_TIME)
                                    : quote("1000", "990", Instant.parse("2026-11-06T06:30:00Z")));
        }

        JapanQuoteCollector.Result result = collector.collect(COLLECTION_DATE);

        assertThat(result.saved()).isFalse();
        assertThat(result.reason()).startsWith(JapanQuoteCollector.WAITING_TODAY_REASON);
        assertThat(result.fetchFailed()).isFalse();
        verify(repository, never()).saveAll(any());
    }

    @Test
    void 개장_직후_시각만_오늘이고_값은_어제_그대로면_오늘_시세로_세지_않고_기다린다() {
        // 2026-11-27 09:18 첫 회차: 야후가 225종목 모두 시각은 오늘 09:00으로 주면서 현재가·전일 종가는 어제 응답 그대로였다.
        // 그대로 저장되어 첫 지도와 텔레그램 이미지가 비었다.
        List<OverseasStockQuote> last = new ArrayList<>();
        for (int index = 0; index < 10; index++) {
            last.add(lastQuote("30" + index + "0", "1000.00", "990.00", "1000000"));
            when(client.fetchTokyoQuote("30" + index + "0")).thenReturn(quote("1000", "990", OPENING_TIME));
        }
        givenLastQuotes(last.toArray(OverseasStockQuote[]::new));

        JapanQuoteCollector.Result result = collector.collect(COLLECTION_DATE);

        assertThat(result.saved()).isFalse();
        assertThat(result.reason()).startsWith(JapanQuoteCollector.WAITING_TODAY_REASON);
        assertThat(result.todayQuoteCount()).isZero();
        assertThat(result.otherDateQuoteCount()).isEqualTo(10);
        verify(repository, never()).saveAll(any());
    }

    @Test
    void 개장_직후_전일_종가가_어제_종가로_바뀌었으면_값이_같아도_오늘_시세로_센다() {
        // 어제 종가 1000으로 시작해 아직 움직이지 않은 종목: 전일 종가가 어제 종가(1000)로 넘어와 있으면 오늘 시세다.
        List<OverseasStockQuote> last = new ArrayList<>();
        for (int index = 0; index < 10; index++) {
            last.add(lastQuote("40" + index + "0", "1000.00", "990.00", "1000000"));
            when(client.fetchTokyoQuote("40" + index + "0")).thenReturn(quote("1000", "1000", OPENING_TIME));
        }
        givenLastQuotes(last.toArray(OverseasStockQuote[]::new));

        JapanQuoteCollector.Result result = collector.collect(COLLECTION_DATE);

        assertThat(result.saved()).isTrue();
        assertThat(result.todayQuoteCount()).isEqualTo(10);
        assertThat(savedQuotes().getFirst().getLastPrice()).isEqualByComparingTo("1000");
    }

    @Test
    void 같은_날_장중에는_값이_그대로여도_오늘_시세로_센다() {
        // 장중 회차의 기준 시세는 오늘 것이라 현재가·전일 종가가 같아도 어제 응답이 아니다.
        LocalDateTime morning = LocalDateTime.of(2026, 11, 9, 10, 0);
        OverseasStockQuote base = OverseasStockQuote.create(
                new OverseasStockQuoteId("JAPAN", "7203", morning),
                new BigDecimal("1000.00"),
                new BigDecimal("990.00"),
                BigDecimal.ZERO,
                new BigDecimal("1000000"));
        when(repository.findFirstByIdMarketOrderByIdSnapshotTimeDesc("JAPAN")).thenReturn(Optional.of(base));
        when(repository.findAllByIdMarketAndIdSnapshotTime("JAPAN", morning)).thenReturn(List.of(base));
        when(client.fetchTokyoQuote("7203")).thenReturn(quote("1000", "990", MARKET_TIME));

        JapanQuoteCollector.Result result = collector.collect(COLLECTION_DATE);

        assertThat(result.saved()).isTrue();
        assertThat(result.todayQuoteCount()).isEqualTo(1);
    }

    @Test
    @SuppressWarnings("unchecked")
    void 오늘_시세와_함께_야후의_오늘_일봉을_일봉_표에_넣는다() {
        givenLastQuotes(lastQuote("7203", "1000.00", "1000000"));
        when(client.fetchTokyoQuote("7203"))
                .thenReturn(new YahooFinanceQuoteClient.Quote(
                        new BigDecimal("1100"),
                        new BigDecimal("1000"),
                        MARKET_TIME,
                        new YahooFinanceQuoteClient.TodayBar(
                                new BigDecimal("1050"), new BigDecimal("1120"), new BigDecimal("1040"), 12345L)));

        assertThat(collector.collect(COLLECTION_DATE).saved()).isTrue();

        ArgumentCaptor<List<OverseasStockDaily>> captor = ArgumentCaptor.forClass(List.class);
        verify(dailies).saveAll(captor.capture());
        OverseasStockDaily daily = captor.getValue().getFirst();
        assertThat(daily.getId().getTradeDate()).isEqualTo(COLLECTION_DATE);
        assertThat(daily.getOpenPrice()).isEqualByComparingTo("1050");
        assertThat(daily.getHighPrice()).isEqualByComparingTo("1120");
        assertThat(daily.getLowPrice()).isEqualByComparingTo("1040");
        assertThat(daily.getClosePrice()).isEqualByComparingTo("1100");
        assertThat(daily.getVolume()).isEqualTo(12345L);
    }

    // 오늘 시세가 온 종목 todayCount개와 거래 정지로 어제 값만 오는 2914(기준 시세 포함).
    private void givenTodayQuotesWithHalted(int todayCount) {
        java.util.List<OverseasStockQuote> last = new java.util.ArrayList<>();
        for (int index = 0; index < todayCount; index++) {
            last.add(lastQuote("20" + index + "0", "1000.00", "1000000"));
            when(client.fetchTokyoQuote("20" + index + "0")).thenReturn(quote("1100", "1000", MARKET_TIME));
        }
        last.add(lastQuote("2914", "6269.00", "1000000"));
        givenLastQuotes(last.toArray(OverseasStockQuote[]::new));
    }

    @Test
    void 종목_시세를_저장한_뒤_닛케이_지수를_같은_시각으로_저장한다() {
        givenLastQuotes(lastQuote("7203", "1000.00", "1000000"));
        when(client.fetchTokyoQuote("7203")).thenReturn(quote("1100", "1000", MARKET_TIME));
        when(client.fetchQuote("^N225")).thenReturn(quote("61142.35", "61077.90", MARKET_TIME));

        JapanQuoteCollector.Result result = collector.collect(COLLECTION_DATE);

        assertThat(result.saved()).isTrue();
        ArgumentCaptor<OverseasIndexQuote> captor = ArgumentCaptor.forClass(OverseasIndexQuote.class);
        verify(indexQuotes).save(captor.capture());
        OverseasIndexQuote saved = captor.getValue();
        assertThat(saved.getId().getMarket()).isEqualTo("NIKKEI225");
        assertThat(saved.getId().getSnapshotTime()).isEqualTo(result.snapshotTime());
        assertThat(saved.getCurrentValue()).isEqualByComparingTo("61142.35");
        assertThat(saved.getLastValue()).isEqualByComparingTo("61077.90");
        assertThat(saved.getChangeRate()).isEqualByComparingTo("0.11");
    }

    @Test
    void 지수를_받지_못해도_종목_시세는_저장한다() {
        givenLastQuotes(lastQuote("7203", "1000.00", "1000000"));
        when(client.fetchTokyoQuote("7203")).thenReturn(quote("1100", "1000", MARKET_TIME));
        when(client.fetchQuote("^N225")).thenThrow(new IllegalStateException("지수 없음"));

        JapanQuoteCollector.Result result = collector.collect(COLLECTION_DATE);

        assertThat(result.saved()).isTrue();
        assertThat(savedQuotes()).hasSize(1);
        verify(indexQuotes, never()).save(any());
    }

    @Test
    void 지수_응답이_비어_있으면_저장하지_않고_종목_시세는_저장한다() {
        givenLastQuotes(lastQuote("7203", "1000.00", "1000000"));
        when(client.fetchTokyoQuote("7203")).thenReturn(quote("1100", "1000", MARKET_TIME));
        when(client.fetchQuote("^N225")).thenReturn(null);

        assertThat(collector.collect(COLLECTION_DATE).saved()).isTrue();
        verify(indexQuotes, never()).save(any());
    }

    @Test
    void 종목_시세를_저장하지_않으면_지수도_저장하지_않는다() {
        givenLastQuotes(lastQuote("7203", "1000.00", "1000000"));
        when(client.fetchTokyoQuote("7203")).thenReturn(quote("1000", "990", Instant.parse("2026-11-06T06:30:02Z")));
        when(client.fetchQuote("^N225")).thenReturn(quote("61142.35", "61077.90", MARKET_TIME));

        assertThat(collector.collect(COLLECTION_DATE).saved()).isFalse();
        verify(client, never()).fetchQuote(anyString());
        verify(indexQuotes, never()).save(any());
    }

    private void givenLastQuotes(OverseasStockQuote... quotes) {
        when(repository.findFirstByIdMarketOrderByIdSnapshotTimeDesc("JAPAN")).thenReturn(Optional.of(quotes[0]));
        when(repository.findAllByIdMarketAndIdSnapshotTime("JAPAN", LAST_TIME)).thenReturn(List.of(quotes));
    }

    @SuppressWarnings("unchecked")
    private List<OverseasStockQuote> savedQuotes() {
        ArgumentCaptor<Iterable<OverseasStockQuote>> captor = ArgumentCaptor.forClass(Iterable.class);
        verify(repository).saveAll(captor.capture());
        return (List<OverseasStockQuote>) captor.getValue();
    }

    private static OverseasStockQuote lastQuote(String code, String price, String marketValue) {
        return lastQuote(code, price, price, marketValue);
    }

    private static OverseasStockQuote lastQuote(String code, String price, String lastPrice, String marketValue) {
        return OverseasStockQuote.create(
                new OverseasStockQuoteId("JAPAN", code, LAST_TIME),
                new BigDecimal(price),
                new BigDecimal(lastPrice),
                BigDecimal.ZERO,
                new BigDecimal(marketValue));
    }

    private static YahooFinanceQuoteClient.Quote quote(String price, String previousClose, Instant time) {
        return new YahooFinanceQuoteClient.Quote(new BigDecimal(price), new BigDecimal(previousClose), time);
    }
}
