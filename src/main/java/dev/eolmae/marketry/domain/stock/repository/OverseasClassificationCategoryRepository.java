package dev.eolmae.marketry.domain.stock.repository;

import dev.eolmae.marketry.domain.stock.entity.OverseasClassificationCategory;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface OverseasClassificationCategoryRepository extends JpaRepository<OverseasClassificationCategory, Long> {
    List<OverseasClassificationCategory> findAllByMarket(String market);
}
