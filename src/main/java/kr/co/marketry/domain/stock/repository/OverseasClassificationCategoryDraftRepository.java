package kr.co.marketry.domain.stock.repository;

import java.util.List;
import kr.co.marketry.domain.stock.entity.OverseasClassificationCategoryDraft;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface OverseasClassificationCategoryDraftRepository
        extends JpaRepository<OverseasClassificationCategoryDraft, Long> {
    List<OverseasClassificationCategoryDraft> findAllByMarket(String market);

    boolean existsByMarket(String market);

    // 파생 삭제는 행을 하나씩 지워서 자기 참조 키(parent_id) 순서에 걸린다. 한 문장으로 지워야 해서 쿼리를 쓴다.
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("delete from OverseasClassificationCategoryDraft draft where draft.market = :market")
    void deleteAllByMarket(@Param("market") String market);
}
