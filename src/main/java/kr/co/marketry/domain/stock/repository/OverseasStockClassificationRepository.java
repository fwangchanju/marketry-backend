package kr.co.marketry.domain.stock.repository;

import java.util.List;
import kr.co.marketry.domain.stock.entity.OverseasStockClassification;
import kr.co.marketry.domain.stock.entity.OverseasStockClassificationId;
import org.springframework.data.jpa.repository.JpaRepository;

public interface OverseasStockClassificationRepository
        extends JpaRepository<OverseasStockClassification, OverseasStockClassificationId> {
    List<OverseasStockClassification> findAllByIdMarket(String market);
}
