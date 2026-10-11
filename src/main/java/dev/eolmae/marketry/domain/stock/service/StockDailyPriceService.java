package dev.eolmae.marketry.domain.stock.service;

import dev.eolmae.marketry.common.exception.BadRequestException;
import dev.eolmae.marketry.common.exception.ErrorCode;
import dev.eolmae.marketry.domain.stock.dto.MissingDailyPriceStock;
import dev.eolmae.marketry.domain.stock.entity.StockDailyPrice;
import dev.eolmae.marketry.domain.stock.enums.ExchangeType;
import dev.eolmae.marketry.domain.stock.repository.StockDailyPriceRepository;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.function.Function;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class StockDailyPriceService {
    private final StockDailyPriceRepository repository;

    @Transactional(timeout = 5)
    public SaveResult savePage(List<StockDailyPrice> prices) {
        if (Thread.currentThread().isInterrupted()) {
            throw new BadRequestException(ErrorCode.COLLECTOR_EXECUTION_FAILED, "일봉 수집 중단");
        }
        if (prices.isEmpty()) {
            return new SaveResult(0, 0);
        }
        var first = prices.getFirst();
        var dates = new HashSet<java.time.LocalDate>();
        for (var price : prices) {
            if (price.getExchangeType() == ExchangeType.SOR
                    || first.getStockCode().equals(price.getStockCode()) == false
                    || first.getExchangeType() != price.getExchangeType()
                    || dates.add(price.getTradeDate()) == false) {
                throw new BadRequestException(ErrorCode.INVALID_INPUT, "일봉 페이지의 종목·거래소 혼합 또는 중복 날짜");
            }
        }
        var startDate = prices.stream()
                .map(StockDailyPrice::getTradeDate)
                .min(java.time.LocalDate::compareTo)
                .orElseThrow();
        var endDate = prices.stream()
                .map(StockDailyPrice::getTradeDate)
                .max(java.time.LocalDate::compareTo)
                .orElseThrow();
        var existing = repository
                .findByStockCodeAndExchangeTypeAndTradeDateBetween(
                        first.getStockCode(), first.getExchangeType(), startDate, endDate)
                .stream()
                .collect(Collectors.toMap(StockDailyPrice::getTradeDate, Function.identity()));
        List<StockDailyPrice> added = new ArrayList<>();
        int skipped = 0;
        // 페이지 단위로 기존 값을 조회하여 종목·날짜별 개별 SELECT를 피한다.
        for (var price : prices) {
            var stored = existing.get(price.getTradeDate());
            if (stored == null) {
                added.add(price);
            } else {
                skipped++;
            }
        }
        repository.saveAll(added);
        repository.flush();
        if (Thread.currentThread().isInterrupted()) {
            throw new BadRequestException(ErrorCode.COLLECTOR_EXECUTION_FAILED, "일봉 저장 중단");
        }
        return new SaveResult(added.size(), skipped);
    }

    @Transactional(readOnly = true, timeout = 5)
    public List<MissingDailyPriceStock> findMissing(LocalDate date, List<String> stockCodes) {
        return repository.findMissing(date, stockCodes);
    }

    public record SaveResult(int inserted, int skipped) {}
}
