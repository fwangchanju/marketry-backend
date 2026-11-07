package kr.co.marketry.domain.stock.service;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Map;
import kr.co.marketry.common.util.KstClock;
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

    private static final int CALENDAR_TRUSTED_DAYS = 14;

    private final ClosingPriceCacheService closingPriceCacheService;
    private final StockDailyPriceService stockDailyPriceService;
    private final MarketCalendarTimeService marketCalendarTimeService;

    public ClosingPrices closingPricesFor(LocalDate baseDate, LocalDate today) {
        if (hasOfficialClose(baseDate)) {
            return new ClosingPrices(baseDate, stockDailyPriceService.findKrxClosePrices(baseDate));
        }
        CalendarDayTimes times = marketCalendarTimeService.resolve(baseDate);
        return closingPriceCacheService.getFor(baseDate, KstClock.now(), times);
    }

    /** date의 직전 거래일 종가 — 공통 거래일 판정으로 찾으며, 찾을 수 없으면 가격이 비어 있다. */
    public ClosingPrices previousClosingPricesFor(LocalDate date) {
        return marketCalendarTimeService
                .findPreviousTradingDay(date)
                .map(previous -> closingPricesFor(actualPreviousDay(previous, date), date))
                .orElseGet(() -> new ClosingPrices(date, Map.of()));
    }

    /** 그 날짜 일봉의 기준가(종가 − 전일 대비). 분할일에도 등락률이 맞다. */
    public Map<String, BigDecimal> basePricesFor(LocalDate date) {
        return stockDailyPriceService.findKrxBasePrices(date);
    }

    /**
     * 달력이 없는 옛 날짜는 평일 판정이 휴장일을 고를 수 있어, 그 날에 일봉이 없으면 일봉이 있는 가장 가까운 앞 날짜를 쓴다. 최근 날짜는
     * 일봉이 아직 수집되지 않았을 수 있어 그대로 둔다.
     */
    private LocalDate actualPreviousDay(LocalDate previous, LocalDate date) {
        if (date.isAfter(KstClock.now().toLocalDate().minusDays(CALENDAR_TRUSTED_DAYS))
                || stockDailyPriceService.existsKrxPrice(previous)) {
            return previous;
        }
        return stockDailyPriceService.findLatestKrxDateBefore(date).orElse(previous);
    }

    /** 그 날짜의 종가 데이터(KRX)가 있고 거래일이면 종가 데이터가 종가의 기준이다. 존재 여부만 날짜 단위로 확인한다. */
    public boolean hasOfficialClose(LocalDate date) {
        return stockDailyPriceService.existsKrxPrice(date) && marketCalendarTimeService.isTradingDay(date);
    }
}
