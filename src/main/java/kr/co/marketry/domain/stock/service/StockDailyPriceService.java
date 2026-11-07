package kr.co.marketry.domain.stock.service;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import kr.co.marketry.common.exception.BadRequestException;
import kr.co.marketry.common.exception.ErrorCode;
import kr.co.marketry.domain.stock.dto.DailyClosePrice;
import kr.co.marketry.domain.stock.entity.StockDailyPrice;
import kr.co.marketry.domain.stock.enums.ExchangeType;
import kr.co.marketry.domain.stock.repository.StockDailyPriceRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class StockDailyPriceService {
    private final StockDailyPriceRepository repository;

    @Transactional(timeout = 5)
    public SaveResult savePage(List<StockDailyPrice> prices) {
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
        return new SaveResult(added.size(), skipped);
    }

    /** 과거 데이터 받기용. 없는 날짜는 넣고, 있는 날짜는 가격을 그대로 둔 채 전일 대비와 수정 사유만 채운다. */
    @Transactional(timeout = 30)
    public SaveResult saveHistoryPage(List<StockDailyPrice> prices) {
        if (prices.isEmpty()) {
            return new SaveResult(0, 0);
        }
        var first = prices.getFirst();
        var startDate = prices.stream()
                .map(StockDailyPrice::getTradeDate)
                .min(LocalDate::compareTo)
                .orElseThrow();
        var endDate = prices.stream()
                .map(StockDailyPrice::getTradeDate)
                .max(LocalDate::compareTo)
                .orElseThrow();
        var existing = repository
                .findByStockCodeAndExchangeTypeAndTradeDateBetween(
                        first.getStockCode(), first.getExchangeType(), startDate, endDate)
                .stream()
                .collect(Collectors.toMap(StockDailyPrice::getTradeDate, Function.identity()));
        List<StockDailyPrice> added = new ArrayList<>();
        int updated = 0;
        for (var price : prices) {
            var stored = existing.get(price.getTradeDate());
            if (stored == null) {
                added.add(price);
            } else {
                stored.applyAdjustment(price.getChangeValue(), price.getAdjustType(), price.getAdjustRate());
                updated++;
            }
        }
        repository.saveAll(added);
        repository.flush();
        return new SaveResult(added.size(), updated);
    }

    @Transactional(readOnly = true)
    public boolean existsKrxPrice(LocalDate date) {
        return repository.existsByExchangeTypeAndTradeDate(ExchangeType.KRX, date);
    }

    /** 그 날짜 KRX 일봉의 종가. 거래정지 등으로 종가가 0 이하인 종목은 가격이 없는 것으로 보고 뺀다. */
    @Transactional(readOnly = true)
    public Map<String, BigDecimal> findKrxClosePrices(LocalDate date) {
        return repository.findByExchangeTypeAndTradeDate(ExchangeType.KRX, date).stream()
                .filter(price -> price.closePrice().signum() > 0)
                .collect(Collectors.toUnmodifiableMap(DailyClosePrice::stockCode, DailyClosePrice::closePrice));
    }

    @Transactional(readOnly = true)
    public Optional<LocalDate> findLatestKrxDateBefore(LocalDate date) {
        return repository.findLatestTradeDateBefore(ExchangeType.KRX, date);
    }

    /** 그 날짜의 기준가(종가 − 전일 대비). 전일 대비가 없는 종목은 빠진다. */
    @Transactional(readOnly = true)
    public Map<String, BigDecimal> findKrxBasePrices(LocalDate date) {
        return repository.findBasePrices(ExchangeType.KRX, date).stream()
                .filter(price -> price.closePrice().signum() > 0)
                .collect(Collectors.toUnmodifiableMap(DailyClosePrice::stockCode, DailyClosePrice::closePrice));
    }

    /** dates 중 KRX 일봉이 하나라도 있는 날짜. 캘린더 행이 없는 날짜만 날짜별 존재 여부로 따로 확인한다. */
    @Transactional(readOnly = true)
    public Set<LocalDate> findKrxPriceDates(Collection<LocalDate> dates) {
        if (dates.isEmpty()) {
            return Set.of();
        }
        LocalDate from = dates.stream().min(LocalDate::compareTo).orElseThrow();
        LocalDate to = dates.stream().max(LocalDate::compareTo).orElseThrow();
        Set<LocalDate> priceDates = new HashSet<>(repository.findCalendarDatesWithPrice(ExchangeType.KRX, from, to));
        priceDates.retainAll(dates);
        for (LocalDate date : dates) {
            if (priceDates.contains(date) == false && existsKrxPrice(date)) {
                priceDates.add(date);
            }
        }
        return priceDates;
    }

    public record SaveResult(int inserted, int skipped) {}
}
