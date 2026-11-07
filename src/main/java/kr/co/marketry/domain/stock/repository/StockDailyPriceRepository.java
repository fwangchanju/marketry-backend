package kr.co.marketry.domain.stock.repository;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import kr.co.marketry.domain.stock.dto.DailyClosePrice;
import kr.co.marketry.domain.stock.entity.StockDailyPrice;
import kr.co.marketry.domain.stock.enums.ExchangeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface StockDailyPriceRepository
        extends JpaRepository<StockDailyPrice, Long>, StockDailyPriceRepositoryCustom {
    List<StockDailyPrice> findByStockCodeAndExchangeTypeAndTradeDateBetween(
            String stockCode, ExchangeType exchangeType, LocalDate startDate, LocalDate endDate);

    @Query("select max(p.tradeDate) from StockDailyPrice p where p.exchangeType = :exchange and p.tradeDate < :date")
    Optional<LocalDate> findLatestTradeDateBefore(
            @Param("exchange") ExchangeType exchange, @Param("date") LocalDate date);

    /** 전일 대비로 구한 그날의 기준가(종가 − 전일 대비). 분할일에는 분할을 반영한 값이다. */
    @Query("select new kr.co.marketry.domain.stock.dto.DailyClosePrice(p.stockCode, p.closePrice - p.changeValue)"
            + " from StockDailyPrice p where p.exchangeType = :exchange and p.tradeDate = :date"
            + " and p.changeValue is not null and p.closePrice > 0")
    List<DailyClosePrice> findBasePrices(@Param("exchange") ExchangeType exchange, @Param("date") LocalDate date);

    /** 기준가가 전날 종가와 다른 날(분할·병합·감자 등)과 그 비율. KRX 전체를 한 번 훑으므로 결과를 캐시해서 쓴다. */
    @Query(value = """
                    SELECT stock_code AS stockCode, trade_date AS tradeDate, (close_price - change_value) / previous AS factor
                    FROM (
                        SELECT stock_code, trade_date, close_price, change_value,
                               lag(close_price) OVER (PARTITION BY stock_code ORDER BY trade_date) AS previous
                        FROM stock_daily_price
                        WHERE exchange_type = 'KRX' AND close_price > 0
                    ) daily
                    WHERE previous > 0 AND change_value IS NOT NULL
                      AND close_price - change_value > 0
                      AND abs(close_price - change_value - previous) > 1
                    """, nativeQuery = true)
    List<PriceAdjustmentRow> findPriceAdjustments();

    interface PriceAdjustmentRow {
        String getStockCode();

        LocalDate getTradeDate();

        BigDecimal getFactor();
    }

    boolean existsByExchangeTypeAndTradeDate(ExchangeType exchangeType, LocalDate tradeDate);

    /** 종목코드와 종가 열만 읽는다. 날짜·거래소 조건은 (trade_date, exchange_type) 인덱스를 탄다. */
    List<DailyClosePrice> findByExchangeTypeAndTradeDate(ExchangeType exchangeType, LocalDate tradeDate);
}
