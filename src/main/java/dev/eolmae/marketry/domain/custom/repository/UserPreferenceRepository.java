package dev.eolmae.marketry.domain.custom.repository;

import dev.eolmae.marketry.domain.custom.entity.UserPreference;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface UserPreferenceRepository extends JpaRepository<UserPreference, Long> {

    /** 회원 탈퇴 — 한 회원의 개인 설정을 지운다. */
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("delete from UserPreference e where e.userId = :userId")
    void deleteAllOfUser(@Param("userId") Long userId);
}
