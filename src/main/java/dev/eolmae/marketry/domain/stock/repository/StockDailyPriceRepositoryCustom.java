package dev.eolmae.marketry.domain.stock.repository;

import dev.eolmae.marketry.domain.stock.dto.MissingDailyPriceStock;
import java.time.LocalDate;
import java.util.List;

public interface StockDailyPriceRepositoryCustom {
    List<MissingDailyPriceStock> findMissing(LocalDate date, List<String> stockCodes);
}
