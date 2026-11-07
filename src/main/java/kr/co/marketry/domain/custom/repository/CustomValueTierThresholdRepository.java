package kr.co.marketry.domain.custom.repository;

import java.util.List;
import kr.co.marketry.domain.custom.entity.CustomValueTierThreshold;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CustomValueTierThresholdRepository extends JpaRepository<CustomValueTierThreshold, Long> {

    List<CustomValueTierThreshold> findAllByUserIdOrderByThresholdValueAsc(Long userId);

    void deleteAllByUserId(Long userId);
}
