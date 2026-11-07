package kr.co.marketry.domain.stock.repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import kr.co.marketry.domain.stock.entity.OverseasStockQuote;
import kr.co.marketry.domain.stock.entity.OverseasStockQuoteId;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface OverseasStockQuoteRepository
        extends JpaRepository<OverseasStockQuote, OverseasStockQuoteId>, OverseasStockQuoteRepositoryCustom {
    /** 그날의 마지막 시각만 남기고 같은 날의 나머지 시세를 지운다. */
    @Modifying
    @Query(value = """
                    DELETE FROM overseas_stock_quote
                    WHERE market = :market AND snapshot_time >= :dayStart AND snapshot_time < :dayEnd
                      AND snapshot_time < :keep
                    """, nativeQuery = true)
    int deleteDayExceptLast(
            @Param("market") String market,
            @Param("dayStart") LocalDateTime dayStart,
            @Param("dayEnd") LocalDateTime dayEnd,
            @Param("keep") LocalDateTime keep);

    List<OverseasStockQuote> findAllByIdMarketAndIdStockCodeAndIdSnapshotTimeAfter(
            String market, String stockCode, LocalDateTime snapshotTime);

    List<OverseasStockQuote> findAllByIdMarketAndIdSnapshotTime(String market, LocalDateTime snapshotTime);

    Optional<OverseasStockQuote> findFirstByIdMarketOrderByIdSnapshotTimeDesc(String market);
}
