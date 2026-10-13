package dev.eolmae.marketry.domain.stock.repository;

import dev.eolmae.marketry.domain.stock.entity.StockTradingSuspension;
import java.time.LocalDate;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface StockTradingSuspensionRepository extends JpaRepository<StockTradingSuspension, Long> {

    /** 아직 풀리지 않은(재개 전) 거래정지. */
    List<StockTradingSuspension> findByEndedOnIsNull();

    /** 제외가 시작됐고 아직 재개되지 않은 거래정지. */
    List<StockTradingSuspension> findByExcludedFromLessThanEqualAndEndedOnIsNull(LocalDate date);

    /** {@code date}에 제외가 시작된 거래정지. */
    List<StockTradingSuspension> findByExcludedFrom(LocalDate date);

    /** 제외가 시작됐고 {@code date} 이후에 재개된 거래정지. */
    List<StockTradingSuspension> findByExcludedFromLessThanEqualAndEndedOnGreaterThan(
            LocalDate date, LocalDate endedAfter);
}
