package kr.co.marketry.domain.auth.service;

import kr.co.marketry.common.event.IndustryInfoCreatedEvent;
import lombok.RequiredArgsConstructor;
import org.springframework.context.event.EventListener;
import org.springframework.jdbc.core.ConnectionCallback;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class IndustrySectorPropagationService {

    private final JdbcTemplate jdbcTemplate;

    @EventListener
    @Transactional
    public void onIndustryInfoCreated(IndustryInfoCreatedEvent event) {
        // pg_advisory_xact_lock — 가입·업종 전파를 직렬화하는 PostgreSQL 세션 락. JPQL/QueryDSL에는
        // 대응하는 문법이 없다.
        jdbcTemplate.execute((ConnectionCallback<Void>) connection -> {
            try (var statement = connection.prepareStatement("SELECT pg_advisory_xact_lock(?)")) {
                statement.setLong(1, SignupInitializationService.INDUSTRY_USER_SYNC_LOCK);
                statement.execute();
            }
            return null;
        });
        // INSERT ... SELECT + ON CONFLICT DO NOTHING — 전체 사용자에게 새 업종 섹터를 한 번에
        // 전파하는 집합 연산. JPA 엔티티를 사용자 수만큼 개별 생성하지 않고 DB에서 직접 처리한다.
        jdbcTemplate.update("""
                INSERT INTO custom_sector (user_id, parent_id, name, depth, is_excluded, created_at, updated_at)
                SELECT users.id, NULL, ?, 0, FALSE, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP
                FROM users
                ON CONFLICT (user_id, name) DO NOTHING
                """, event.name());
    }
}
