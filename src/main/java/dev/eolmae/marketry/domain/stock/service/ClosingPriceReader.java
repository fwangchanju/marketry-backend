package dev.eolmae.marketry.domain.stock.service;

import dev.eolmae.marketry.common.util.KstClock;
import java.time.LocalDate;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/**
 * 날짜별 종가를 읽는다. 종가(D)는 D의 종가 데이터(stock_daily_price에 쌓는 키움 종가(일봉) 요청 API 수집분, KRX)가 있는지 날짜만 확인해서
 * 있으면 종가 데이터를, 없으면 현재가 데이터(sector_price_snapshot에 쌓는 키움 현재가 요청 API 수집분)의 종가 시각 가격을 쓰는 하나의
 * 규칙이다. 전일 종가도 같은 규칙으로 직전 거래일을 기준으로 읽는다. 종가 데이터가 있는 날짜의 지도는 이 값을 전일 종가로 쓰고, 현재가
 * 데이터로 만드는 날짜의 지도는 현재가 − 변동액을 구하지 못한 종목의 대체값으로만 쓴다.
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

    /** 그 날짜의 종가 데이터(KRX)가 있고 거래일이면 종가 데이터가 종가의 기준이다. 존재 여부만 날짜 단위로 확인한다. */
    public boolean hasOfficialClose(LocalDate date) {
        return stockDailyPriceService.existsKrxPrice(date) && marketCalendarTimeService.isTradingDay(date);
    }
}
