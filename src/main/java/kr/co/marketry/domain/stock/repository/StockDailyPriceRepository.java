package kr.co.marketry.domain.stock.repository;

import java.time.LocalDate;
import java.util.List;
import kr.co.marketry.domain.stock.dto.DailyClosePrice;
import kr.co.marketry.domain.stock.entity.StockDailyPrice;
import kr.co.marketry.domain.stock.enums.ExchangeType;
import org.springframework.data.jpa.repository.JpaRepository;

public interface StockDailyPriceRepository
        extends JpaRepository<StockDailyPrice, Long>, StockDailyPriceRepositoryCustom {
    List<StockDailyPrice> findByStockCodeAndExchangeTypeAndTradeDateBetween(
            String stockCode, ExchangeType exchangeType, LocalDate startDate, LocalDate endDate);

    boolean existsByExchangeTypeAndTradeDate(ExchangeType exchangeType, LocalDate tradeDate);

    /** 종목코드와 종가 열만 읽는다. 날짜·거래소 조건은 (trade_date, exchange_type) 인덱스를 탄다. */
    List<DailyClosePrice> findByExchangeTypeAndTradeDate(ExchangeType exchangeType, LocalDate tradeDate);
}
