package kr.co.marketry.domain.custom.repository;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import kr.co.marketry.domain.custom.entity.CustomScaleThreshold;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface CustomScaleThresholdRepository extends JpaRepository<CustomScaleThreshold, Long> {

    List<CustomScaleThreshold> findAllByUserId(Long userId);

    boolean existsByUserIdAndThresholdPercent(Long userId, BigDecimal thresholdPercent);

    boolean existsByUserIdAndThresholdPercentAndIdNot(Long userId, BigDecimal thresholdPercent, Long id);

    Optional<CustomScaleThreshold> findByIdAndUserId(Long id, Long userId);

    void deleteByIdAndUserId(Long id, Long userId);

    void deleteAllByUserId(Long userId);

    /** 회원 탈퇴 — 한 회원의 등락률 색상 구간을 모두 지운다. */
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("delete from CustomScaleThreshold e where e.userId = :userId")
    void deleteAllOfUser(@Param("userId") Long userId);
}
