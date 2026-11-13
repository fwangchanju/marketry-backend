package kr.co.marketry.domain.stock.repository;

import java.time.LocalDate;
import java.util.List;
import kr.co.marketry.domain.stock.entity.MarketIndexDaily;
import org.springframework.data.jpa.repository.JpaRepository;

public interface MarketIndexDailyRepository extends JpaRepository<MarketIndexDaily, MarketIndexDaily.Id> {
    List<MarketIndexDaily> findByIdTradeDateBetweenOrderByIdTradeDateAsc(LocalDate from, LocalDate to);

    List<MarketIndexDaily> findByIdMarketAndIdTradeDateBetweenOrderByIdTradeDateAsc(
            String market, LocalDate from, LocalDate to);
}
