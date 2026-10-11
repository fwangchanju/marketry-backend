package dev.eolmae.marketry.domain.stock.repository;

import dev.eolmae.marketry.domain.stock.entity.StockDailyPrice;
import dev.eolmae.marketry.domain.stock.enums.ExchangeType;
import java.time.LocalDate;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface StockDailyPriceRepository
        extends JpaRepository<StockDailyPrice, Long>, StockDailyPriceRepositoryCustom {
    List<StockDailyPrice> findByStockCodeAndExchangeTypeAndTradeDateBetween(
            String stockCode, ExchangeType exchangeType, LocalDate startDate, LocalDate endDate);
}
