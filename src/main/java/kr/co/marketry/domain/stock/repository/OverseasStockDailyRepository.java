package kr.co.marketry.domain.stock.repository;

import java.time.LocalDate;
import java.util.List;
import kr.co.marketry.domain.stock.entity.OverseasStockDaily;
import org.springframework.data.jpa.repository.JpaRepository;

public interface OverseasStockDailyRepository extends JpaRepository<OverseasStockDaily, OverseasStockDaily.Id> {
    List<OverseasStockDaily> findByIdMarketAndIdStockCodeAndIdTradeDateGreaterThanEqualOrderByIdTradeDateAsc(
            String market, String stockCode, LocalDate startDate);
}
