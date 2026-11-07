package kr.co.marketry.domain.custom.repository;

import jakarta.persistence.LockModeType;
import java.util.List;
import kr.co.marketry.domain.custom.entity.CustomStockSector;
import kr.co.marketry.domain.custom.entity.CustomStockSectorId;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;

public interface CustomStockSectorRepository
        extends JpaRepository<CustomStockSector, CustomStockSectorId>, CustomStockSectorRepositoryCustom {

    List<CustomStockSector> findAllByIdUserId(Long userId);

    List<CustomStockSector> findByIdUserIdAndSectorId(Long userId, Long sectorId);

    /** 업종에 담긴 배정 행을 FOR UPDATE로 잠근다 — 삭제하려고 센 종목 수와 옮길 종목이 같은 상태에서 움직이게 한다. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    List<CustomStockSector> findLockedByIdUserIdAndSectorId(Long userId, Long sectorId);

    List<CustomStockSector> findByIdUserIdAndSectorIdIn(Long userId, List<Long> sectorIds);

    List<CustomStockSector> findByIdUserIdAndIdStockCodeIn(Long userId, List<String> stockCodes);
}
