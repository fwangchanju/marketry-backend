package dev.eolmae.marketry.domain.auth.repository;

import dev.eolmae.marketry.domain.auth.entity.UserProfile;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface UserProfileRepository extends JpaRepository<UserProfile, Long> {

    <T> Optional<T> findByUserId(Long userId, Class<T> type);

    boolean existsByNicknameIgnoreCaseAndUserIdNot(String nickname, Long userId);

    /** 회원 탈퇴 — 한 회원의 프로필(닉네임·사진)을 지운다. */
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("delete from UserProfile p where p.userId = :userId")
    void deleteAllOfUser(@Param("userId") Long userId);
}
