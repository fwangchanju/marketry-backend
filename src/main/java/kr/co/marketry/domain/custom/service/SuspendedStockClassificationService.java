package kr.co.marketry.domain.custom.service;

import java.util.Collection;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import kr.co.marketry.common.event.StockSuspensionSyncedEvent;
import kr.co.marketry.common.util.KstClock;
import kr.co.marketry.domain.auth.service.SignupInitializationService;
import kr.co.marketry.domain.custom.enums.ProtectedSector;
import kr.co.marketry.domain.notification.properties.MarketryProperties;
import kr.co.marketry.domain.stock.service.StockTradingSuspensionService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.event.EventListener;
import org.springframework.jdbc.core.ConnectionCallback;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.PreparedStatementSetter;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 거래정지 종목을 사용자의 "거래 정지" 업종에 자동으로 배정하고, 재개가 확인된 종목은 "미분류"로 돌려보낸다.
 * 사용자가 직접 배정한 종목도 덮어쓰고, 몇 번 다시 돌려도 결과가 같다. MARKETRY 발행 사용자는 고정본이라 건드리지 않는다.
 * 자동 이동은 사용자가 한 분류가 아니므로 수정 시각(updated_at)을 바꾸지 않는다.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SuspendedStockClassificationService {

    private static final String SUSPENDED_NAME = ProtectedSector.SUSPENDED.getSectorName();
    private static final String UNCLASSIFIED_NAME = ProtectedSector.UNCLASSIFIED.getSectorName();

    private final JdbcTemplate jdbcTemplate;
    private final ProtectedSectorService protectedSectorService;
    private final StockTradingSuspensionService suspensionService;
    private final MarketryProperties marketryProperties;

    /** 거래정지 기록을 맞춘 직후: 5거래일에 이른 종목은 거래 정지로, 재개가 확인된 종목은 미분류로 모든 사용자에게 옮긴다. */
    @EventListener
    @Transactional
    public void onSuspensionSynced(StockSuspensionSyncedEvent event) {
        apply(event.excludedStockCodes(), event.resumedStockCodes(), null);
    }

    /** 앱 시작 때: 지금 정지로 제외 중인 종목을 모든 사용자의 거래 정지 업종에 맞춘다. */
    @Transactional
    public void applyCurrentExclusionsToAllUsers() {
        apply(currentlyExcludedStockCodes(), Set.of(), null);
    }

    /** 전체 초기화·스냅샷 복원·업로드 뒤: 한 사용자의 거래 정지 종목을 다시 맞춘다. */
    @Transactional
    public void applyCurrentExclusionsToUser(Long userId) {
        apply(currentlyExcludedStockCodes(), Set.of(), userId);
    }

    /**
     * 사용자가 옮길 수 없는 종목 — 지금 거래 정지로 제외 중이거나, 이 사용자의 거래 정지 업종에 들어 있는 종목이다.
     * 재개가 확인되기 전까지는 거래 정지 업종에 남으므로 둘을 함께 본다.
     */
    @Transactional(readOnly = true)
    public Set<String> findLockedStockCodes(Long userId, Collection<String> stockCodes) {
        Set<String> locked = new HashSet<>(currentlyExcludedStockCodes());
        locked.retainAll(stockCodes);
        if (stockCodes.isEmpty()) {
            return locked;
        }
        // = ANY(배열) 조회라 파생 메서드로 표현할 수 없어 JdbcTemplate을 쓴다.
        List<String> assignedToSuspended = jdbcTemplate.query(
                """
                SELECT assignment.stock_code
                FROM custom_stock_sector assignment
                JOIN custom_sector sector ON sector.id = assignment.sector_id
                WHERE assignment.user_id = ?
                  AND sector.parent_id IS NULL
                  AND sector.name = ?
                  AND assignment.stock_code = ANY(?::varchar[])
                """,
                statement -> {
                    statement.setLong(1, userId);
                    statement.setString(2, SUSPENDED_NAME);
                    statement.setArray(3, statement.getConnection().createArrayOf("varchar", stockCodes.toArray()));
                },
                (resultSet, rowNumber) -> resultSet.getString(1));
        locked.addAll(assignedToSuspended);
        return locked;
    }

    private Set<String> currentlyExcludedStockCodes() {
        return suspensionService.findExcludedStockCodes(KstClock.now().toLocalDate());
    }

    private void apply(Set<String> excludedStockCodes, Set<String> resumedStockCodes, Long onlyUserId) {
        if (excludedStockCodes.isEmpty() && resumedStockCodes.isEmpty()) {
            return;
        }
        // pg_advisory_xact_lock — 가입·업종·신규 종목 전파와 같은 락으로 직렬화한다. JPQL/QueryDSL에는 대응하는 문법이 없다.
        jdbcTemplate.execute((ConnectionCallback<Void>) connection -> {
            try (var statement = connection.prepareStatement("SELECT pg_advisory_xact_lock(?)")) {
                statement.setLong(1, SignupInitializationService.INDUSTRY_USER_SYNC_LOCK);
                statement.execute();
            }
            return null;
        });
        if (onlyUserId == null) {
            protectedSectorService.ensureForAllUsers();
        } else {
            protectedSectorService.ensureForUser(onlyUserId);
        }
        Long publishedUserId = marketryProperties.publishedUserId();
        if (onlyUserId != null && onlyUserId.equals(publishedUserId)) {
            return;
        }
        int movedToSuspended = moveToSuspended(excludedStockCodes, publishedUserId, onlyUserId);
        int movedToUnclassified = moveToUnclassified(resumedStockCodes, publishedUserId, onlyUserId);
        if (movedToSuspended > 0 || movedToUnclassified > 0) {
            log.info("[거래 정지 분류] 거래 정지로 {}건, 미분류로 {}건 옮겼다", movedToSuspended, movedToUnclassified);
        }
    }

    private int moveToSuspended(Set<String> stockCodes, Long publishedUserId, Long onlyUserId) {
        if (stockCodes.isEmpty()) {
            return 0;
        }
        // INSERT ... SELECT + unnest + ON CONFLICT DO UPDATE — (전체 사용자 × 정지 종목)을 문장 하나로 처리하는 집합 연산이다.
        // 이미 거래 정지 업종에 있는 행은 건드리지 않고, 옮기는 행도 updated_at은 그대로 둔다.
        return jdbcTemplate.update("""
                INSERT INTO custom_stock_sector (user_id, stock_code, sector_id, created_at, updated_at)
                SELECT users.id, codes.stock_code, suspended.id, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP
                FROM users
                CROSS JOIN unnest(?::varchar[]) AS codes(stock_code)
                JOIN custom_sector suspended
                  ON suspended.user_id = users.id AND suspended.parent_id IS NULL AND suspended.name = ?
                WHERE users.id IS DISTINCT FROM ?::bigint
                  AND (?::bigint IS NULL OR users.id = ?::bigint)
                ON CONFLICT (user_id, stock_code)
                DO UPDATE SET sector_id = EXCLUDED.sector_id
                WHERE custom_stock_sector.sector_id <> EXCLUDED.sector_id
                """, (PreparedStatementSetter) statement -> {
            statement.setArray(
                    1, statement.getConnection().createArrayOf("varchar", new LinkedHashSet<>(stockCodes).toArray()));
            statement.setString(2, SUSPENDED_NAME);
            statement.setObject(3, publishedUserId);
            statement.setObject(4, onlyUserId);
            statement.setObject(5, onlyUserId);
        });
    }

    private int moveToUnclassified(Set<String> stockCodes, Long publishedUserId, Long onlyUserId) {
        if (stockCodes.isEmpty()) {
            return 0;
        }
        // UPDATE ... FROM — 거래 정지 업종에 있는 재개 종목을 같은 사용자의 미분류 업종으로 옮기는 집합 연산이다. updated_at은 그대로 둔다.
        return jdbcTemplate.update("""
                UPDATE custom_stock_sector assignment
                SET sector_id = unclassified.id
                FROM custom_sector suspended, custom_sector unclassified
                WHERE assignment.stock_code = ANY(?::varchar[])
                  AND suspended.id = assignment.sector_id
                  AND suspended.parent_id IS NULL
                  AND suspended.name = ?
                  AND unclassified.user_id = assignment.user_id
                  AND unclassified.parent_id IS NULL
                  AND unclassified.name = ?
                  AND assignment.user_id IS DISTINCT FROM ?::bigint
                  AND (?::bigint IS NULL OR assignment.user_id = ?::bigint)
                """, (PreparedStatementSetter) statement -> {
            statement.setArray(
                    1, statement.getConnection().createArrayOf("varchar", new LinkedHashSet<>(stockCodes).toArray()));
            statement.setString(2, SUSPENDED_NAME);
            statement.setString(3, UNCLASSIFIED_NAME);
            statement.setObject(4, publishedUserId);
            statement.setObject(5, onlyUserId);
            statement.setObject(6, onlyUserId);
        });
    }
}
