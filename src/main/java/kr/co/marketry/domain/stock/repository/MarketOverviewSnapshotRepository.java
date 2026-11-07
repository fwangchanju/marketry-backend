package kr.co.marketry.domain.stock.repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import kr.co.marketry.common.enums.Market;
import kr.co.marketry.domain.stock.entity.*;
import org.springframework.data.jpa.repository.JpaRepository;

public interface MarketOverviewSnapshotRepository extends JpaRepository<MarketOverviewSnapshot, Long> {

    boolean existsByMarketTypeAndSnapshotTime(Market market, LocalDateTime snapshotTime);

    Optional<MarketOverviewSnapshot> findFirstByOrderBySnapshotTimeDesc();

    List<MarketOverviewSnapshot> findBySnapshotTime(LocalDateTime snapshotTime);

    List<MarketOverviewSnapshot> findBySnapshotTimeGreaterThanEqualAndSnapshotTimeLessThanOrderBySnapshotTimeAsc(
            LocalDateTime from, LocalDateTime to);
}
