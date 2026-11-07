package kr.co.marketry.domain.custom.service;

import kr.co.marketry.domain.custom.enums.ProtectedSector;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 보호 업종(신규 상장·거래 정지·미분류)이 항상 있게 만든다. 없는 것만 대분류로 추가하고 이미 있는 업종은 건드리지 않는다.
 * 같은 이름이 하위 업종에 이미 있으면 (user_id, name) 유니크 제약 때문에 추가되지 않으니, 그 사용자는 업종을 먼저 정리해야 한다.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ProtectedSectorService {

    private final JdbcTemplate jdbcTemplate;

    /** 한 사용자에게 없는 보호 업종을 추가한다. 가입·전체 초기화·스냅샷 복원·업로드 뒤에 부른다. */
    @Transactional
    public void ensureForUser(Long userId) {
        for (String name : ProtectedSector.sectorNames()) {
            // INSERT ... ON CONFLICT DO NOTHING — 이미 있으면 건너뛰는 동작이 JPQL/QueryDSL에 없다.
            jdbcTemplate.update("""
                    INSERT INTO custom_sector (user_id, parent_id, name, depth, is_excluded, created_at, updated_at)
                    VALUES (?, NULL, ?, 0, FALSE, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)
                    ON CONFLICT (user_id, name) DO NOTHING
                    """, userId, name);
        }
    }

    /** 모든 사용자에게 없는 보호 업종을 추가한다. 앱 시작 때 한 번 부른다. 이미 있으면 아무것도 바꾸지 않는다. */
    @Transactional
    public int ensureForAllUsers() {
        int created = 0;
        for (String name : ProtectedSector.sectorNames()) {
            // INSERT ... SELECT + ON CONFLICT DO NOTHING — 전체 사용자를 문장 하나로 처리하는 집합 연산이다.
            created += jdbcTemplate.update("""
                    INSERT INTO custom_sector (user_id, parent_id, name, depth, is_excluded, created_at, updated_at)
                    SELECT users.id, NULL, ?, 0, FALSE, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP
                    FROM users
                    ON CONFLICT (user_id, name) DO NOTHING
                    """, name);
        }
        if (created > 0) {
            log.info("[보호 업종] 없던 업종 {}개를 추가했다", created);
        }
        return created;
    }
}
