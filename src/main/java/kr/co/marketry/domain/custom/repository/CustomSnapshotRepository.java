package kr.co.marketry.domain.custom.repository;

import java.util.List;
import kr.co.marketry.domain.custom.entity.CustomSnapshot;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface CustomSnapshotRepository extends JpaRepository<CustomSnapshot, Long> {

    List<CustomSnapshot> findAllByUserIdOrderByCreatedAtDesc(Long userId);

    java.util.Optional<CustomSnapshot> findByIdAndUserId(Long id, Long userId);

    void deleteByIdAndUserId(Long id, Long userId);

    /** 회원 탈퇴 — 한 회원의 스냅샷을 모두 지운다. 업종이 스냅샷을 가리키므로 업종을 먼저 지운 뒤에 부른다. */
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("delete from CustomSnapshot e where e.userId = :userId")
    void deleteAllOfUser(@Param("userId") Long userId);
}
