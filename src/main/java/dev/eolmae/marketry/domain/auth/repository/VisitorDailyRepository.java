package dev.eolmae.marketry.domain.auth.repository;

import dev.eolmae.marketry.domain.auth.entity.VisitorDaily;
import dev.eolmae.marketry.domain.auth.entity.VisitorDailyId;
import java.time.LocalDate;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface VisitorDailyRepository
        extends JpaRepository<VisitorDaily, VisitorDailyId>, VisitorDailyRepositoryCustom {

    /** 보관 기간이 지난 날짜의 방문 기록을 지운다. */
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("delete from VisitorDaily v where v.id.visitDate < :before")
    int deleteVisitedBefore(@Param("before") LocalDate before);
}
