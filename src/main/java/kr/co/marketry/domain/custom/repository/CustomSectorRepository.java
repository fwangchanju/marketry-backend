package kr.co.marketry.domain.custom.repository;

import jakarta.persistence.LockModeType;
import java.util.List;
import java.util.Optional;
import kr.co.marketry.domain.custom.entity.CustomSector;
import kr.co.marketry.domain.custom.enums.ClassificationMarket;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface CustomSectorRepository extends JpaRepository<CustomSector, Long>, CustomSectorRepositoryCustom {

    List<CustomSector> findAllByUserIdAndMarket(Long userId, ClassificationMarket market);

    Optional<CustomSector> findFirstByUserIdAndMarketOrderByIdAsc(Long userId, ClassificationMarket market);

    Optional<CustomSector> findByIdAndUserIdAndMarket(Long id, Long userId, ClassificationMarket market);

    /** 업종 행을 FOR UPDATE로 잠근다. 외래키가 커밋 시점에 검사돼 동시 배정을 즉시 막지는 못하고, 겹치면 진 쪽이 외래키 위반으로 실패한다. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    Optional<CustomSector> findLockedByIdAndUserIdAndMarket(Long id, Long userId, ClassificationMarket market);

    boolean existsByUserIdAndMarketAndName(Long userId, ClassificationMarket market, String name);

    /** 전체 초기화 — 한 회원의 한 시장 업종을 모두 지운다. 종목 배정을 먼저 지운 뒤에 부른다. */
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("delete from CustomSector e where e.userId = :userId and e.market = :market")
    void deleteAllOfUserInMarket(@Param("userId") Long userId, @Param("market") ClassificationMarket market);

    /** 회원 탈퇴 — 한 회원의 모든 시장 업종을 지운다(상위·하위가 한 문장에 함께 지워진다). 종목 배정을 먼저 지운 뒤에 부른다. */
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("delete from CustomSector e where e.userId = :userId")
    void deleteAllOfWithdrawnUser(@Param("userId") Long userId);
}
