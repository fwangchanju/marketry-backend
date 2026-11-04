package dev.eolmae.marketry.domain.stock.repository;

import dev.eolmae.marketry.domain.stock.entity.OverseasStockClassification;
import dev.eolmae.marketry.domain.stock.entity.OverseasStockClassificationId;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface OverseasStockClassificationRepository
        extends JpaRepository<OverseasStockClassification, OverseasStockClassificationId> {
    List<OverseasStockClassification> findAllByIdMarket(String market);
}
