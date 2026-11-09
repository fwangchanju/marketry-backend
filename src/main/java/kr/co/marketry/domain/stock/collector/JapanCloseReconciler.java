package kr.co.marketry.domain.stock.collector;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import kr.co.marketry.domain.stock.client.YahooFinanceQuoteClient;
import kr.co.marketry.domain.stock.client.YahooFinanceQuoteClient.DailyBar;
import kr.co.marketry.domain.stock.entity.OverseasStockQuote;
import kr.co.marketry.domain.stock.entity.OverseasStockQuoteId;
import kr.co.marketry.domain.stock.repository.OverseasStockQuoteRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClientResponseException;

/** 야후 일봉으로 같은 날짜의 15:30 시세를 보정한다. */
@Slf4j
@Component
public class JapanCloseReconciler {
    private static final String MARKET = "JAPAN";
    private static final LocalTime CLOSE_TIME = LocalTime.of(15, 30);
    private static final int MAX_PASSES = 3;
    private final YahooFinanceQuoteClient client;
    private final OverseasStockQuoteRepository repository;
    private final Set<String> expectedStocks;

    @Autowired
    public JapanCloseReconciler(YahooFinanceQuoteClient client, OverseasStockQuoteRepository repository) {
        this(client, repository, JapanMarketReference.constituentCodes());
    }

    JapanCloseReconciler(
            YahooFinanceQuoteClient client, OverseasStockQuoteRepository repository, Set<String> expectedStocks) {
        this.client = client;
        this.repository = repository;
        this.expectedStocks = expectedStocks;
    }

    public Result reconcile(LocalDate date) {
        var latest =
                repository.findFirstByIdMarketOrderByIdSnapshotTimeDesc(MARKET).orElse(null);
        if (latest == null || latest.getId().getSnapshotTime().toLocalDate().isAfter(date)) {
            throw new IllegalStateException("일본 종목 기준 시세가 없거나 대상 날짜보다 늦습니다");
        }
        List<OverseasStockQuote> baseline = repository.findAllByIdMarketAndIdSnapshotTime(
                MARKET, latest.getId().getSnapshotTime());
        Set<String> actual = baseline.stream()
                .map(quote -> quote.getId().getStockCode())
                .collect(java.util.stream.Collectors.toSet());
        if (!actual.equals(expectedStocks)) {
            Set<String> missing = new java.util.TreeSet<>(expectedStocks);
            missing.removeAll(actual);
            Set<String> extra = new java.util.TreeSet<>(actual);
            extra.removeAll(expectedStocks);
            return new Result(0, List.of("기준 종목 불일치: 누락=" + missing + " 추가=" + extra));
        }
        if (latest.getId().getSnapshotTime().equals(date.atTime(CLOSE_TIME))
                && baseline.stream().allMatch(quote -> Boolean.TRUE.equals(quote.getCloseVerified()))) {
            log.info("[일본 종가 확인] 당일 종가가 이미 확인되어 건너뜁니다. 날짜={}", date);
            return new Result(0, List.of());
        }

        // 지수 일봉이 없으면 휴장인지 제공 지연인지 판별할 수 없다. 보정하지 않고 알린다.
        boolean tradingDay = false;
        for (int pass = 0; pass < MAX_PASSES && !tradingDay; pass++) {
            try {
                tradingDay = client.fetchDailyBars("^N225", date.minusDays(14), date).stream()
                        .anyMatch(bar -> bar.date().equals(date));
            } catch (RuntimeException failure) {
                log.warn(
                        "[일본 지수 일봉 확인 실패] 날짜={} 회차={} 오류={}",
                        date,
                        pass + 1,
                        failure.getClass().getSimpleName());
            }
            if (!tradingDay && pass + 1 < MAX_PASSES) {
                try {
                    Thread.sleep(5000);
                } catch (InterruptedException interrupted) {
                    Thread.currentThread().interrupt();
                    return new Result(0, List.of("닛케이 일봉 확인 중단"));
                }
            }
        }
        if (!tradingDay) return new Result(0, List.of("닛케이 일봉 없음: 휴장 또는 야후 제공 지연 확인 필요"));

        Map<String, OverseasStockQuote> pending = new LinkedHashMap<>();
        baseline.forEach(quote -> pending.put(quote.getId().getStockCode(), quote));
        Map<String, OverseasStockQuote> corrected = new LinkedHashMap<>();
        for (int pass = 0; pass < MAX_PASSES && !pending.isEmpty(); pass++) {
            for (String code : new ArrayList<>(pending.keySet())) {
                try {
                    List<DailyBar> bars = client.fetchDailyBars(code + ".T", date.minusDays(14), date).stream()
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
                    BigDecimal price = today.close().setScale(2, RoundingMode.HALF_UP);
                    BigDecimal prior = previous.close().setScale(2, RoundingMode.HALF_UP);
                    OverseasStockQuote old = pending.get(code);
                    BigDecimal cap = old.getTotalMarketValue()
                            .multiply(price)
                            .divide(old.getCurrentPrice(), 0, RoundingMode.HALF_UP);
                    OverseasStockQuote quote = OverseasStockQuote.create(
                            new OverseasStockQuoteId(MARKET, code, date.atTime(CLOSE_TIME)),
                            price,
                            prior,
                            price.subtract(prior)
                                    .multiply(BigDecimal.valueOf(100))
                                    .divide(prior, 2, RoundingMode.HALF_UP),
                            cap);
                    quote.markCloseVerified();
                    corrected.put(code, quote);
                    pending.remove(code);
                } catch (RuntimeException failure) {
                    log.warn(
                            "[일본 종가 확인 실패] 날짜={} 종목={} 회차={} 오류={}",
                            date,
                            code,
                            pass + 1,
                            failure.getClass().getSimpleName());
                    if (failure instanceof RestClientResponseException response
                            && response.getStatusCode().isSameCodeAs(HttpStatus.TOO_MANY_REQUESTS)) {
                        try {
                            if (pass + 1 < MAX_PASSES) Thread.sleep(5000);
                        } catch (InterruptedException interrupted) {
                            Thread.currentThread().interrupt();
                            return new Result(0, List.copyOf(pending.keySet()));
                        }
                        break;
                    }
                }
                try {
                    Thread.sleep(200);
                } catch (InterruptedException interrupted) {
                    Thread.currentThread().interrupt();
                    return new Result(0, List.copyOf(pending.keySet()));
                }
            }
        }
        // 같은 시각의 225종목이 이미 있으면 성공 종목만 보정해도 나머지 종목이 유지된다.
        // 새 스냅샷을 만들 때는 일부만 저장하지 않는다. 다음 수집 대상이 누락되기 때문이다.
        if (!pending.isEmpty() && !latest.getId().getSnapshotTime().equals(date.atTime(CLOSE_TIME))) {
            return new Result(0, List.copyOf(pending.keySet()));
        }
        if (corrected.isEmpty()) return new Result(0, List.copyOf(pending.keySet()));
        repository.saveAll(corrected.values());
        return new Result(corrected.size(), List.copyOf(pending.keySet()));
    }

    public record Result(int saved, List<String> failures) {}
}
