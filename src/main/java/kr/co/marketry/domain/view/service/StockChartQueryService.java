package kr.co.marketry.domain.view.service;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.TreeMap;
import kr.co.marketry.common.enums.Market;
import kr.co.marketry.common.enums.Zone;
import kr.co.marketry.domain.stock.entity.OverseasStockDaily;
import kr.co.marketry.domain.stock.entity.StockDailyPrice;
import kr.co.marketry.domain.stock.enums.ExchangeType;
import kr.co.marketry.domain.stock.repository.OverseasStockDailyRepository;
import kr.co.marketry.domain.stock.repository.OverseasStockQuoteRepository;
import kr.co.marketry.domain.stock.repository.SectorPriceSnapshotRepository;
import kr.co.marketry.domain.stock.repository.StockDailyPriceRepository;
import kr.co.marketry.domain.view.dto.StockDailyCandle;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
@RequiredArgsConstructor
public class StockChartQueryService {
    public static final int MAX_DAYS = 3650;
    private static final String JAPAN_MARKET = "JAPAN";
    private static final String USA_MARKET = "USA";
    // 장중 임시 봉은 정규장(한국 KRX·일본 도쿄 모두 09:00~15:30, 한국 시각) 5분 시세로만 만든다. 한국 시간외·NXT 값은 쓰지 않는다.
    private static final LocalTime REGULAR_OPEN = LocalTime.of(9, 0);
    private static final LocalTime REGULAR_CLOSE = LocalTime.of(15, 30);
    // 임시 봉을 찾을 5분 시세 범위 — 주말·휴일을 건너도 마지막 거래일이 들어오게 며칠 넉넉히 본다.
    private static final int INTRADAY_LOOKBACK_DAYS = 5;

    private final StockDailyPriceRepository stockDailyPriceRepository;
    private final OverseasStockDailyRepository overseasStockDailyRepository;
    private final SectorPriceSnapshotRepository sectorPriceSnapshotRepository;
    private final OverseasStockQuoteRepository overseasStockQuoteRepository;

    /**
     * 최근 days일 안의 KRX 정규장 일봉을 오래된 날부터 준다. 거래정지일의 0 가격은 차트를 깨므로 뺀다. 아직 일봉이 저장되지 않은 최근
     * 거래일(장중)은 5분 시세로 만든 임시 봉을 끝에 붙인다.
     */
    public List<StockDailyCandle> getDailyCandles(String stockCode, int days) {
        LocalDate startDate = LocalDate.now(Zone.KST.zoneId()).minusDays(Math.min(days, MAX_DAYS));
        List<StockDailyCandle> candles = stockDailyPriceRepository
                .findByStockCodeAndExchangeTypeAndTradeDateGreaterThanEqualOrderByTradeDateAsc(
                        stockCode, ExchangeType.KRX, startDate)
                .stream()
                .filter(price -> price.getClosePrice().signum() > 0)
                .map(StockChartQueryService::toCandle)
                .toList();
        List<IntradayPrice> intraday = sectorPriceSnapshotRepository
                .findByMarketTypeInAndStockCodeAndSnapshotTimeAfterOrderBySnapshotTimeAsc(
                        List.of(Market.KOSPI, Market.KOSDAQ), stockCode, intradaySince())
                .stream()
                .map(snapshot -> new IntradayPrice(snapshot.getSnapshotTime(), snapshot.getCurrentPrice()))
                .toList();
        return withProvisional(candles, intraday);
    }

    /** 최근 days일 안의 일본 종목 일봉을 오래된 날부터 준다. 아직 일봉이 저장되지 않은 최근 거래일은 5분 시세로 만든 임시 봉을 붙인다. */
    public List<StockDailyCandle> getJapanDailyCandles(String stockCode, int days) {
        List<IntradayPrice> intraday = overseasStockQuoteRepository
                .findAllByIdMarketAndIdStockCodeAndIdSnapshotTimeAfter(JAPAN_MARKET, stockCode, intradaySince())
                .stream()
                .map(quote -> new IntradayPrice(quote.getId().getSnapshotTime(), quote.getCurrentPrice()))
                .toList();
        return withProvisional(getOverseasDailyCandles(JAPAN_MARKET, stockCode, days), intraday);
    }

    private static LocalDateTime intradaySince() {
        return LocalDate.now(Zone.KST.zoneId())
                .minusDays(INTRADAY_LOOKBACK_DAYS)
                .atStartOfDay();
    }

    record IntradayPrice(LocalDateTime time, BigDecimal price) {}

    /**
     * 5분 시세 중 가장 최근 거래일이 마지막 일봉보다 뒤면, 그날 정규장 값으로 임시 봉(첫 값·최고·최저·마지막 값, 거래량 0)을 만들어 붙인다. 같은
     * 시각에 값이 여럿이면(거래소별 행) 시각 순서대로 다 쓴다.
     */
    static List<StockDailyCandle> withProvisional(List<StockDailyCandle> candles, List<IntradayPrice> intraday) {
        TreeMap<LocalDate, List<IntradayPrice>> byDate = new TreeMap<>();
        for (IntradayPrice point : intraday) {
            LocalTime time = point.time().toLocalTime();
            if (point.price() == null || point.price().signum() <= 0) continue;
            if (time.isBefore(REGULAR_OPEN) || time.isAfter(REGULAR_CLOSE)) continue;
            byDate.computeIfAbsent(point.time().toLocalDate(), date -> new ArrayList<>())
                    .add(point);
        }
        if (byDate.isEmpty()) return candles;
        LocalDate latestDate = byDate.lastKey();
        LocalDate lastCandleDate =
                candles.isEmpty() ? null : candles.get(candles.size() - 1).date();
        if (lastCandleDate != null && !latestDate.isAfter(lastCandleDate)) return candles;
        List<IntradayPrice> day = byDate.get(latestDate);
        day.sort(Comparator.comparing(IntradayPrice::time));
        BigDecimal high = day.stream()
                .map(IntradayPrice::price)
                .max(BigDecimal::compareTo)
                .orElseThrow();
        BigDecimal low = day.stream()
                .map(IntradayPrice::price)
                .min(BigDecimal::compareTo)
                .orElseThrow();
        List<StockDailyCandle> result = new ArrayList<>(candles);
        result.add(new StockDailyCandle(
                latestDate,
                day.get(0).price(),
                high,
                low,
                day.get(day.size() - 1).price(),
                0,
                true));
        return result;
    }

    /** 최근 days일 안의 미국 종목 일봉을 오래된 날부터 준다. */
    public List<StockDailyCandle> getUsaDailyCandles(String stockCode, int days) {
        return getOverseasDailyCandles(USA_MARKET, stockCode, days);
    }

    private List<StockDailyCandle> getOverseasDailyCandles(String market, String stockCode, int days) {
        LocalDate startDate = LocalDate.now(Zone.KST.zoneId()).minusDays(Math.min(days, MAX_DAYS));
        return overseasStockDailyRepository
                .findByIdMarketAndIdStockCodeAndIdTradeDateGreaterThanEqualOrderByIdTradeDateAsc(
                        market, stockCode, startDate)
                .stream()
                .map(StockChartQueryService::toCandle)
                .toList();
    }

    private static StockDailyCandle toCandle(OverseasStockDaily price) {
        return new StockDailyCandle(
                price.getId().getTradeDate(),
                price.getOpenPrice(),
                price.getHighPrice(),
                price.getLowPrice(),
                price.getClosePrice(),
                price.getVolume());
    }

    private static StockDailyCandle toCandle(StockDailyPrice price) {
        return new StockDailyCandle(
                price.getTradeDate(),
                price.getOpenPrice(),
                price.getHighPrice(),
                price.getLowPrice(),
                price.getClosePrice(),
                price.getVolume());
    }
}
