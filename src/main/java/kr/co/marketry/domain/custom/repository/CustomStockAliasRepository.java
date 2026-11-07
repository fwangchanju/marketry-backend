package kr.co.marketry.domain.custom.repository;

import java.util.List;
import kr.co.marketry.domain.custom.entity.CustomStockAlias;
import kr.co.marketry.domain.custom.entity.CustomStockAliasId;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface CustomStockAliasRepository extends JpaRepository<CustomStockAlias, CustomStockAliasId> {

    List<CustomStockAlias> findAllByIdUserId(Long userId);

    /** 회원 탈퇴 — 한 회원의 종목 약칭을 모두 지운다. */
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("delete from CustomStockAlias e where e.id.userId = :userId")
    void deleteAllOfUser(@Param("userId") Long userId);
}
