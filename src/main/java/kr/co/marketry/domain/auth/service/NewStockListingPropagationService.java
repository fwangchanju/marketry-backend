package kr.co.marketry.domain.auth.service;

import kr.co.marketry.common.event.StockInfoSyncedEvent;
import kr.co.marketry.domain.custom.entity.CustomSector;
import lombok.RequiredArgsConstructor;
import org.springframework.context.event.EventListener;
import org.springframework.jdbc.core.ConnectionCallback;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.PreparedStatementSetter;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class NewStockListingPropagationService {

    private static final String LISTING_SECTOR_NAME = CustomSector.NEW_LISTING_NAME;

    private final JdbcTemplate jdbcTemplate;

    @EventListener
    @Transactional
    public void onStockInfoSynced(StockInfoSyncedEvent event) {
        if (event.stockCodes().isEmpty()) {
            return;
        }

        // pg_advisory_xact_lock — 가입·업종·신규 종목 전파를 직렬화하는 PostgreSQL 세션 락. JPQL/QueryDSL에는
        // 대응하는 문법이 없다.
        jdbcTemplate.execute((ConnectionCallback<Void>) connection -> {
            try (var statement = connection.prepareStatement("SELECT pg_advisory_xact_lock(?)")) {
                statement.setLong(1, SignupInitializationService.INDUSTRY_USER_SYNC_LOCK);
                statement.execute();
            }
            return null;
        });

        // INSERT ... SELECT + ON CONFLICT DO NOTHING — 전체 사용자에게 `신규 상장` 섹터를 한 번에 확보하는
        // 집합 연산. JPA로 바꾸면 사용자 수만큼 조회·INSERT가 나가고, ON CONFLICT는 JPQL/QueryDSL에 없다.
        jdbcTemplate.update("""
                INSERT INTO custom_sector (user_id, parent_id, name, depth, is_excluded, created_at, updated_at)
                SELECT users.id, NULL, ?, 0, FALSE, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP
                FROM users
                ON CONFLICT (user_id, name) DO NOTHING
                """, LISTING_SECTOR_NAME);

        // INSERT ... SELECT + unnest — (전체 사용자 × 신규 종목)을 문장 하나로 배정하는 집합 연산. 행 수가
        // 사용자 수에 비례해 JPA로는 그만큼 INSERT가 나간다. unnest·ON CONFLICT는 JPQL/QueryDSL에 없다.
        jdbcTemplate.update("""
                INSERT INTO custom_stock_sector (user_id, stock_code, sector_id, created_at, updated_at)
                SELECT users.id, listed.stock_code, sector.id, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP
                FROM users
                CROSS JOIN unnest(?::varchar[]) AS listed(stock_code)
                JOIN custom_sector sector ON sector.user_id = users.id AND sector.name = ?
                ON CONFLICT (user_id, stock_code) DO NOTHING
                """, (PreparedStatementSetter) statement -> {
            statement.setArray(
                    1,
                    statement
                            .getConnection()
                            .createArrayOf("varchar", event.stockCodes().toArray()));
            statement.setString(2, LISTING_SECTOR_NAME);
        });
    }
}
