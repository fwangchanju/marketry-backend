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

    // 종목 차트의 장중 임시 봉용 — 고유 키(시장·종목·거래소·시각) 순서라 시장 목록을 같이 넘겨야 색인을 탄다.
    List<SectorPriceSnapshot> findByMarketTypeInAndStockCodeAndSnapshotTimeAfterOrderBySnapshotTimeAsc(
            List<Market> markets, String stockCode, LocalDateTime snapshotTime);
}
