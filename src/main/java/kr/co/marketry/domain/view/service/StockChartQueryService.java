package kr.co.marketry.domain.view.service;

import java.time.LocalDate;
import java.util.List;
import kr.co.marketry.common.enums.Zone;
import kr.co.marketry.domain.stock.entity.OverseasStockDaily;
import kr.co.marketry.domain.stock.entity.StockDailyPrice;
import kr.co.marketry.domain.stock.enums.ExchangeType;
import kr.co.marketry.domain.stock.repository.OverseasStockDailyRepository;
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

    private final StockDailyPriceRepository stockDailyPriceRepository;
    private final OverseasStockDailyRepository overseasStockDailyRepository;

    /** 최근 days일 안의 KRX 정규장 일봉을 오래된 날부터 준다. 거래정지일의 0 가격은 차트를 깨므로 뺀다. */
    public List<StockDailyCandle> getDailyCandles(String stockCode, int days) {
        LocalDate startDate = LocalDate.now(Zone.KST.zoneId()).minusDays(Math.min(days, MAX_DAYS));
        return stockDailyPriceRepository
                .findByStockCodeAndExchangeTypeAndTradeDateGreaterThanEqualOrderByTradeDateAsc(
                        stockCode, ExchangeType.KRX, startDate)
                .stream()
                .filter(price -> price.getClosePrice().signum() > 0)
                .map(StockChartQueryService::toCandle)
                .toList();
    }

    /** 최근 days일 안의 일본 종목 일봉을 오래된 날부터 준다. */
    public List<StockDailyCandle> getJapanDailyCandles(String stockCode, int days) {
        LocalDate startDate = LocalDate.now(Zone.KST.zoneId()).minusDays(Math.min(days, MAX_DAYS));
        return overseasStockDailyRepository
                .findByIdMarketAndIdStockCodeAndIdTradeDateGreaterThanEqualOrderByIdTradeDateAsc(
                        JAPAN_MARKET, stockCode, startDate)
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
