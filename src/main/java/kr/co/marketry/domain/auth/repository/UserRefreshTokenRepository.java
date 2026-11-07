package kr.co.marketry.domain.auth.repository;

import jakarta.persistence.LockModeType;
import java.time.LocalDateTime;
import java.util.Optional;
import kr.co.marketry.domain.auth.entity.UserRefreshToken;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface UserRefreshTokenRepository extends JpaRepository<UserRefreshToken, Long> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    Optional<UserRefreshToken> findByTokenHash(String tokenHash);

    // 로그아웃 시점 기준 최근 유예 시간(cutoff) 안에 생성되었거나 교체된, 아직 폐기되지 않은 같은 사용자의
    // 토큰을 일괄 폐기한다. 회전 중인 다른 탭의 새 토큰이 로그아웃 뒤 되살아나는 것을 막기 위함.
    @Modifying(flushAutomatically = true)
    @Query(
            "update UserRefreshToken t set t.revokedAt = :now where t.user.id = :userId and t.revokedAt is null and (t.createdAt >= :cutoff or t.replacedAt >= :cutoff)")
    int revokeTokensCreatedOrReplacedSince(
            @Param("userId") Long userId, @Param("cutoff") LocalDateTime cutoff, @Param("now") LocalDateTime now);

    // 만료됐거나, 폐기·교체된 지 하루가 지난 행을 정리한다.
    @Modifying
    @Query(
            "delete from UserRefreshToken t where t.expiresAt < :now or t.revokedAt < :staleCutoff or t.replacedAt < :staleCutoff")
    int deleteExpiredOrStaleTokens(@Param("now") LocalDateTime now, @Param("staleCutoff") LocalDateTime staleCutoff);

    // 회원 탈퇴 — 한 회원의 갱신 토큰을 모두 지운다.
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("delete from UserRefreshToken t where t.user.id = :userId")
    void deleteAllOfUser(@Param("userId") Long userId);
}
