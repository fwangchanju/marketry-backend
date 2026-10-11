package dev.eolmae.marketry.domain.stock.service;

import dev.eolmae.marketry.common.util.KstClock;
import java.time.LocalDate;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/**
 * 날짜별 종가를 읽는다. 종가(D)는 D의 KRX 일봉이 있으면 일봉 종가를, 없으면(어제인데 아직 수집 전이거나 오늘) 스냅샷의 종가 시각 가격을
 * 쓰는 하나의 규칙이다. 전일 종가는 종가(직전 거래일)다.
 */
@Service
@RequiredArgsConstructor
public class ClosingPriceReader {

    private final ClosingPriceCacheService closingPriceCacheService;
    private final StockDailyPriceService stockDailyPriceService;
    private final MarketCalendarTimeService marketCalendarTimeService;

    public ClosingPrices closingPricesFor(LocalDate baseDate, LocalDate today) {
        if (hasOfficialClose(baseDate)) {
            return new ClosingPrices(baseDate, stockDailyPriceService.findKrxClosePrices(baseDate));
        }
        return closingPriceCacheService.getFor(baseDate, KstClock.now());
    }

    /** date의 직전 거래일 종가 — 공통 거래일 판정으로 찾으며, 찾을 수 없으면 가격이 비어 있다. */
    public ClosingPrices previousClosingPricesFor(LocalDate date) {
        return marketCalendarTimeService
                .findPreviousTradingDay(date)
                .map(previous -> closingPricesFor(previous, date))
                .orElseGet(() -> new ClosingPrices(date, Map.of()));
    }

    /** 그 날짜 KRX 일봉이 있고 거래일이면 일봉이 종가의 기준이다. 일봉 존재 여부만 날짜 단위로 확인한다. */
    public boolean hasOfficialClose(LocalDate date) {
        return stockDailyPriceService.existsKrxPrice(date) && marketCalendarTimeService.isTradingDay(date);
    }
}
