package dev.eolmae.marketry.domain.stock.repository;

import dev.eolmae.marketry.domain.stock.entity.OverseasStockQuote;
import dev.eolmae.marketry.domain.stock.entity.OverseasStockQuoteId;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface OverseasStockQuoteRepository
        extends JpaRepository<OverseasStockQuote, OverseasStockQuoteId>, OverseasStockQuoteRepositoryCustom {
    List<OverseasStockQuote> findAllByIdMarketAndIdSnapshotTime(String market, LocalDateTime snapshotTime);

    Optional<OverseasStockQuote> findFirstByIdMarketOrderByIdSnapshotTimeDesc(String market);
}
