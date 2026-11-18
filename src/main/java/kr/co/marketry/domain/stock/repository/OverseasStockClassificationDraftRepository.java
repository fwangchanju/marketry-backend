package kr.co.marketry.domain.stock.repository;

import java.util.List;
import kr.co.marketry.domain.stock.entity.OverseasStockClassificationDraft;
import kr.co.marketry.domain.stock.entity.OverseasStockClassificationId;
import org.springframework.data.jpa.repository.JpaRepository;

public interface OverseasStockClassificationDraftRepository
        extends JpaRepository<OverseasStockClassificationDraft, OverseasStockClassificationId> {
    List<OverseasStockClassificationDraft> findAllByIdMarket(String market);

    void deleteAllByIdMarket(String market);
}
