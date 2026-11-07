package kr.co.marketry.domain.stock.service;

import java.math.BigDecimal;
import java.math.MathContext;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import kr.co.marketry.domain.stock.enums.ExchangeType;
import kr.co.marketry.domain.stock.repository.StockDailyPriceRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/**
 * 기간 등락률의 기준가를 맞출 비율. 분할·병합·감자처럼 기준가가 바뀐 날의 비율을 기간 안에서 곱한다. 예: 50대 1 분할이면 0.02. 배당락처럼
 * 기준가가 그대로인 날은 비율이 1이라 빠진다.
 *
 * <p>전체 일봉을 훑어 구하므로 일봉 최신 날짜가 바뀔 때만 다시 읽는다.
 */
@Service
@RequiredArgsConstructor
public class PriceAdjustmentService {
    private final StockDailyPriceRepository repository;

    private volatile Cached cached;

    /** from 다음 날부터 to까지의 비율. 비율이 1인 종목은 없다. */
    public Map<String, BigDecimal> factorsBetween(LocalDate from, LocalDate to) {
        Map<String, BigDecimal> factors = new HashMap<>();
        events().forEach((stockCode, stockEvents) -> {
            BigDecimal factor = BigDecimal.ONE;
            for (Event event : stockEvents) {
                if (event.date().isAfter(from) && event.date().isAfter(to) == false) {
                    factor = factor.multiply(event.factor(), MathContext.DECIMAL64);
                }
            }
            if (factor.compareTo(BigDecimal.ONE) != 0) {
                factors.put(stockCode, factor);
            }
        });
        return factors;
    }

    private Map<String, List<Event>> events() {
        LocalDate latest = repository
                .findLatestTradeDateBefore(ExchangeType.KRX, LocalDate.of(9999, 1, 1))
                .orElse(null);
        Cached current = cached;
        if (current != null && current.latest() != null && current.latest().equals(latest)) {
            return current.events();
        }
        Map<String, List<Event>> loaded = new HashMap<>();
        for (var row : repository.findPriceAdjustments()) {
            loaded.computeIfAbsent(row.getStockCode(), ignored -> new ArrayList<>())
                    .add(new Event(row.getTradeDate(), row.getFactor()));
        }
        cached = new Cached(latest, loaded);
        return loaded;
    }

    private record Event(LocalDate date, BigDecimal factor) {}

    private record Cached(LocalDate latest, Map<String, List<Event>> events) {}
}
