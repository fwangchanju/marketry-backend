package kr.co.marketry.domain.stock.repository;

import java.time.LocalDateTime;
import java.util.List;
import kr.co.marketry.domain.stock.entity.OverseasIndexQuote;
import kr.co.marketry.domain.stock.entity.OverseasIndexQuoteId;
import org.springframework.data.jpa.repository.JpaRepository;

public interface OverseasIndexQuoteRepository extends JpaRepository<OverseasIndexQuote, OverseasIndexQuoteId> {
    /** from 이상 to 미만 시각의 값을 오래된 순으로. 하루치를 읽을 때 쓴다. */
    List<OverseasIndexQuote>
            findByIdMarketAndIdSnapshotTimeGreaterThanEqualAndIdSnapshotTimeLessThanOrderByIdSnapshotTimeAsc(
                    String market, LocalDateTime from, LocalDateTime to);
}
