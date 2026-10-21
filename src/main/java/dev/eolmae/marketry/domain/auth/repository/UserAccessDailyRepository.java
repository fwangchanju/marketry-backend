package dev.eolmae.marketry.domain.auth.repository;

import dev.eolmae.marketry.domain.auth.entity.UserAccessDaily;
import dev.eolmae.marketry.domain.auth.entity.UserAccessDailyId;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface UserAccessDailyRepository
        extends JpaRepository<UserAccessDaily, UserAccessDailyId>, UserAccessDailyRepositoryCustom {

    /** 회원 탈퇴 — 한 회원의 일별 접속 기록을 모두 지운다. */
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("delete from UserAccessDaily d where d.id.userId = :userId")
    void deleteAllOfUser(@Param("userId") Long userId);
}
