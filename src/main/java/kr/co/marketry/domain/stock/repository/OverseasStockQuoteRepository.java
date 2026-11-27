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

    /**
     * 같은 날의 지정한 시각 줄만 남기고 나머지를 지운다. 미국은 하루의 마지막 줄이 애프터마켓 시세라서 16:00 종가 줄을 지정해 남긴다.
     * 파생 삭제 메서드는 행마다 지워 하루 수만 줄에는 느려서 직접 쿼리를 쓴다(deleteDayExceptLast와 같은 이유).
     */
    @Modifying
    @Query(value = """
                    DELETE FROM overseas_stock_quote
                    WHERE market = :market AND snapshot_time >= :dayStart AND snapshot_time < :dayEnd
                      AND snapshot_time <> :keep
                    """, nativeQuery = true)
    int deleteDayExcept(
            @Param("market") String market,
            @Param("dayStart") LocalDateTime dayStart,
            @Param("dayEnd") LocalDateTime dayEnd,
            @Param("keep") LocalDateTime keep);

    List<OverseasStockQuote> findAllByIdMarketAndIdStockCodeAndIdSnapshotTimeAfter(
            String market, String stockCode, LocalDateTime snapshotTime);

    List<OverseasStockQuote> findAllByIdMarketAndIdSnapshotTime(String market, LocalDateTime snapshotTime);

    Optional<OverseasStockQuote> findFirstByIdMarketOrderByIdSnapshotTimeDesc(String market);

    /** 그 시각 이전(그 시각 포함)에 저장된 가장 최근 시세 한 줄. 같은 시각이 이미 있으면 그 줄이 나온다. */
    Optional<OverseasStockQuote> findFirstByIdMarketAndIdSnapshotTimeLessThanEqualOrderByIdSnapshotTimeDesc(
            String market, LocalDateTime snapshotTime);
}
