package kr.co.marketry.domain.stock.repository;

import java.util.List;
import kr.co.marketry.domain.stock.entity.OverseasClassificationCategory;
import org.springframework.data.jpa.repository.JpaRepository;

public interface OverseasClassificationCategoryRepository extends JpaRepository<OverseasClassificationCategory, Long> {
    List<OverseasClassificationCategory> findAllByMarket(String market);
}
