package kr.co.marketry.domain.stock.collector;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import kr.co.marketry.common.util.KstClock;
import kr.co.marketry.domain.stock.client.YahooFinanceQuoteClient;
import kr.co.marketry.domain.stock.client.YahooFinanceQuoteClient.DailyBar;
import kr.co.marketry.domain.stock.collector.UsaConstituents.Constituent;
import kr.co.marketry.domain.stock.entity.OverseasStockDaily;
import kr.co.marketry.domain.stock.entity.OverseasStockQuote;
import kr.co.marketry.domain.stock.entity.OverseasStockQuoteId;
import kr.co.marketry.domain.stock.repository.OverseasStockDailyRepository;
import kr.co.marketry.domain.stock.repository.OverseasStockQuoteRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClientResponseException;

/**
 * 마감 뒤 늦게 야후 일봉으로 그날 16:00 종가 줄을 확정 종가로 보정하고, 저장 때 빠진 종목을 채운다(일본 {@link JapanCloseReconciler}와 같은 방식).
 *
 * <p>종가 수집은 마감 10분 뒤에 받은 값을 저장해서 야후의 확정 종가와 조금 다를 수 있고(2026-11-25 MMM 189.93 대 189.83), 그때 조회가 실패한
 * 종목은 저장되지 않는다(2026-11-27 HUBB). 확정된 일봉으로 바꿔 쓰면 다음 날 전일 종가 칸도 맞는다.
 * 종가 줄이 아직 없는 날(휴장일 등)은 아무것도 하지 않는다.
 */
@Slf4j
@Component
public class UsaCloseReconciler {
    private static final String MARKET = "USA";
    private static final LocalTime CLOSE_TIME = LocalTime.of(16, 0);
    private static final int MAX_PASSES = 3;
    private static final int LOOKBACK_DAYS = 14;
    private static final int PRICE_SCALE = 2;
    private static final BigDecimal PERCENT = BigDecimal.valueOf(100);
    private static final Duration REQUEST_INTERVAL = Duration.ofMillis(200);
    private static final Duration RATE_LIMIT_WAIT = Duration.ofSeconds(5);

    private final YahooFinanceQuoteClient client;
    private final OverseasStockQuoteRepository repository;
    private final OverseasStockDailyRepository dailies;
    private final UsaConstituents constituents;
    private final Consumer<Duration> sleeper;

    @Autowired
    public UsaCloseReconciler(
            YahooFinanceQuoteClient client,
            OverseasStockQuoteRepository repository,
            OverseasStockDailyRepository dailies,
            UsaConstituents constituents) {
        this(client, repository, dailies, constituents, UsaCloseReconciler::sleep);
    }

    UsaCloseReconciler(
            YahooFinanceQuoteClient client,
            OverseasStockQuoteRepository repository,
            OverseasStockDailyRepository dailies,
            UsaConstituents constituents,
            Consumer<Duration> sleeper) {
        this.client = client;
        this.repository = repository;
        this.dailies = dailies;
        this.constituents = constituents;
        this.sleeper = sleeper;
    }

    public Result reconcile(LocalDate date) {
        LocalDateTime closeTime = date.atTime(CLOSE_TIME);
        Map<String, OverseasStockQuote> saved = new LinkedHashMap<>();
        for (OverseasStockQuote quote : repository.findAllByIdMarketAndIdSnapshotTime(MARKET, closeTime)) {
            saved.put(quote.getId().getStockCode(), quote);
        }
        if (saved.isEmpty()) {
            log.info("[미국 종가 확인] 종가 줄이 없어 건너뜁니다. 날짜={}", date);
            return new Result(0, List.of());
        }
        List<String> pending = new ArrayList<>();
        for (Constituent constituent : constituents.all()) {
            OverseasStockQuote quote = saved.get(constituent.symbol());
            if (quote == null || Boolean.TRUE.equals(quote.getCloseVerified()) == false) {
                pending.add(constituent.symbol());
            }
        }
        if (pending.isEmpty()) {
            log.info("[미국 종가 확인] 당일 종가가 이미 확인되어 건너뜁니다. 날짜={}", date);
            return new Result(0, List.of());
        }
        Map<String, OverseasStockQuote> previousClose = previousCloseRows(date);
        Map<String, OverseasStockQuote> corrected = new LinkedHashMap<>();
        List<OverseasStockDaily> candles = new ArrayList<>();
        for (int pass = 0; pass < MAX_PASSES && pending.isEmpty() == false; pass++) {
            for (String symbol : new ArrayList<>(pending)) {
                try {
                    List<DailyBar> bars = client.fetchDailyBars(symbol, date.minusDays(LOOKBACK_DAYS), date).stream()
                            .sorted(Comparator.comparing(DailyBar::date))
                            .toList();
                    DailyBar today = bars.stream()
                            .filter(bar -> bar.date().equals(date))
                            .findFirst()
                            .orElseThrow();
                    DailyBar previous = bars.stream()
                            .filter(bar -> bar.date().isBefore(date))
                            .reduce((a, b) -> b)
                            .orElseThrow();
                    OverseasStockQuote base = saved.containsKey(symbol) ? saved.get(symbol) : previousClose.get(symbol);
                    if (base == null) {
                        throw new IllegalStateException("시가총액 기준 없음");
                    }
                    BigDecimal price = today.close().setScale(PRICE_SCALE, RoundingMode.HALF_UP);
                    BigDecimal prior = previous.close().setScale(PRICE_SCALE, RoundingMode.HALF_UP);
                    OverseasStockQuote quote = OverseasStockQuote.create(
                            new OverseasStockQuoteId(MARKET, symbol, closeTime),
                            price,
                            prior,
                            price.subtract(prior).multiply(PERCENT).divide(prior, PRICE_SCALE, RoundingMode.HALF_UP),
                            base.getTotalMarketValue()
                                    .multiply(price)
                                    .divide(base.getCurrentPrice(), 0, RoundingMode.HALF_UP));
                    quote.markCloseVerified();
                    candles.add(OverseasStockDaily.create(
                            MARKET,
                            symbol,
                            date,
                            today.open().setScale(PRICE_SCALE, RoundingMode.HALF_UP),
                            today.high().setScale(PRICE_SCALE, RoundingMode.HALF_UP),
                            today.low().setScale(PRICE_SCALE, RoundingMode.HALF_UP),
                            price,
                            today.volume(),
                            KstClock.now()));
                    corrected.put(symbol, quote);
                    pending.remove(symbol);
                } catch (RuntimeException failure) {
                    log.warn(
                            "[미국 종가 확인 실패] 날짜={} 종목={} 회차={} 오류={}",
                            date,
                            symbol,
                            pass + 1,
                            failure.getClass().getSimpleName());
                    if (failure instanceof RestClientResponseException response
                            && response.getStatusCode().isSameCodeAs(HttpStatus.TOO_MANY_REQUESTS)) {
                        if (pass + 1 < MAX_PASSES) {
                            sleeper.accept(RATE_LIMIT_WAIT);
                        }
                        break;
                    }
                }
                sleeper.accept(REQUEST_INTERVAL);
            }
        }
        saveCandles(candles, date);
        // 같은 시각의 종가 줄이 이미 있으므로 성공한 종목만 바꿔도 나머지는 그대로 남는다.
        if (corrected.isEmpty() == false) {
            repository.saveAll(corrected.values());
        }
        return new Result(corrected.size(), List.copyOf(pending));
    }

    /** 이 날짜 앞의 가장 최근 종가 줄 — 종가 저장 때 빠진 종목의 시가총액을 이어 붙일 기준이다. */
    private Map<String, OverseasStockQuote> previousCloseRows(LocalDate date) {
        LocalDateTime found = null;
        for (LocalDateTime time : repository.findSnapshotTimes(MARKET)) {
            if (CLOSE_TIME.equals(time.toLocalTime()) && time.toLocalDate().isBefore(date)) {
                if (found == null || time.isAfter(found)) {
                    found = time;
                }
            }
        }
        Map<String, OverseasStockQuote> rows = new LinkedHashMap<>();
        if (found != null) {
            for (OverseasStockQuote quote : repository.findAllByIdMarketAndIdSnapshotTime(MARKET, found)) {
                rows.put(quote.getId().getStockCode(), quote);
            }
        }
        return rows;
    }

    /** 종목 차트에 쓸 일봉을 저장한다. 실패해도 종가 보정은 막지 않는다. */
    private void saveCandles(List<OverseasStockDaily> candles, LocalDate date) {
        try {
            dailies.saveAll(candles);
        } catch (RuntimeException failure) {
            log.warn("[미국 종목 일봉 저장 실패] 날짜={} 오류={}", date, failure.getClass().getSimpleName());
        }
    }

    private static void sleep(Duration duration) {
        try {
            Thread.sleep(duration);
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("미국 종가 확인이 중단됐습니다", interrupted);
        }
    }

    public record Result(int saved, List<String> failures) {}
}
