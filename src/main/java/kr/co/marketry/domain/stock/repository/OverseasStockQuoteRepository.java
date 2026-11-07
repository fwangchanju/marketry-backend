package kr.co.marketry.domain.stock.repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import kr.co.marketry.domain.stock.entity.OverseasStockQuote;
import kr.co.marketry.domain.stock.entity.OverseasStockQuoteId;
import org.springframework.data.jpa.repository.JpaRepository;

public interface OverseasStockQuoteRepository
        extends JpaRepository<OverseasStockQuote, OverseasStockQuoteId>, OverseasStockQuoteRepositoryCustom {
    List<OverseasStockQuote> findAllByIdMarketAndIdSnapshotTime(String market, LocalDateTime snapshotTime);

    Optional<OverseasStockQuote> findFirstByIdMarketOrderByIdSnapshotTimeDesc(String market);
}
