package kr.co.marketry.domain.custom.repository;

import java.util.List;
import kr.co.marketry.domain.custom.entity.CustomSnapshot;
import kr.co.marketry.domain.custom.enums.ClassificationMarket;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface CustomSnapshotRepository extends JpaRepository<CustomSnapshot, Long> {

    List<CustomSnapshot> findAllByUserIdAndMarketOrderByCreatedAtDesc(Long userId, ClassificationMarket market);

    java.util.Optional<CustomSnapshot> findByIdAndUserIdAndMarket(Long id, Long userId, ClassificationMarket market);

    void deleteByIdAndUserIdAndMarket(Long id, Long userId, ClassificationMarket market);

    /** 회원 탈퇴 — 한 회원의 모든 시장 스냅샷을 지운다. 업종이 스냅샷을 가리키므로 업종을 먼저 지운 뒤에 부른다. */
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("delete from CustomSnapshot e where e.userId = :userId")
    void deleteAllOfWithdrawnUser(@Param("userId") Long userId);
}
