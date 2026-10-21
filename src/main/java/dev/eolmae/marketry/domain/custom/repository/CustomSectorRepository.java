package dev.eolmae.marketry.domain.custom.repository;

import dev.eolmae.marketry.domain.custom.entity.CustomSector;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface CustomSectorRepository extends JpaRepository<CustomSector, Long> {

    List<CustomSector> findAllByUserId(Long userId);

    List<CustomSector> findByUserIdAndParentId(Long userId, Long parentId);

    Optional<CustomSector> findFirstByUserIdOrderByIdAsc(Long userId);

    Optional<CustomSector> findFirstByUserIdOrderByUpdatedAtDesc(Long userId);

    Optional<CustomSector> findByIdAndUserId(Long id, Long userId);

    boolean existsByUserIdAndName(Long userId, String name);

    void deleteAllByUserId(Long userId);

    /** 회원 탈퇴 — 한 회원의 업종을 모두 지운다(상위·하위가 한 문장에 함께 지워진다). 종목 배정을 먼저 지운 뒤에 부른다. */
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("delete from CustomSector e where e.userId = :userId")
    void deleteAllOfUser(@Param("userId") Long userId);
}
