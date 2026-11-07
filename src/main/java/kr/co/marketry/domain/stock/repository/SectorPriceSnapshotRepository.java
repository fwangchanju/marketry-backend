package kr.co.marketry.domain.stock.repository;

import java.time.LocalDateTime;
import java.util.List;
import kr.co.marketry.common.enums.Market;
import kr.co.marketry.domain.stock.entity.SectorPriceSnapshot;
import org.springframework.data.jpa.repository.JpaRepository;

public interface SectorPriceSnapshotRepository
        extends JpaRepository<SectorPriceSnapshot, Long>, SectorPriceSnapshotRepositoryCustom {

    boolean existsByMarketTypeAndSnapshotTime(Market market, LocalDateTime snapshotTime);

    List<SectorPriceSnapshot> findByMarketTypeInAndSnapshotTime(List<Market> markets, LocalDateTime snapshotTime);
}
