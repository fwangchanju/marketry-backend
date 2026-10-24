package dev.eolmae.marketry.domain.auth.repository;

import dev.eolmae.marketry.domain.auth.dto.AdminDashboardMetricResponse;
import dev.eolmae.marketry.domain.auth.dto.AdminPageMetricResponse;
import dev.eolmae.marketry.domain.auth.dto.ClientMetricEventRequest;
import java.time.LocalDate;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
@RequiredArgsConstructor
public class AdminDashboardMetricRepository {
    // user_access_daily가 만들어진 2026-10-21 이전 가입자는 재방문 관찰 기록이 없으므로 코호트에서 제외한다.
    private static final LocalDate ACCESS_TRACKING_START = LocalDate.of(2026, 10, 21);
    private final JdbcTemplate jdbcTemplate;

    public void record(LocalDate date, ClientMetricEventRequest event) {
        // 날짜·화면·종류별 단일 행을 증가시키는 PostgreSQL upsert는 JPQL/QueryDSL로 표현할 수 없다.
        jdbcTemplate.update("""
                INSERT INTO admin_client_metrics_daily (metric_date, page, metric_type, event_count, total_duration_ms)
                VALUES (?, ?, ?, 1, ?)
                ON CONFLICT (metric_date, page, metric_type)
                DO UPDATE SET event_count = admin_client_metrics_daily.event_count + 1,
                              total_duration_ms = admin_client_metrics_daily.total_duration_ms + EXCLUDED.total_duration_ms
                """, date, event.page(), event.type(), event.durationMs() == null ? 0 : event.durationMs());
    }

    /** 가입일마다 관찰 구간이 달라지는 7일 코호트 집계는 PostgreSQL 날짜 연산을 써서 한 번에 계산한다. */
    public AdminDashboardMetricResponse summarize(LocalDate today, int days) {
        LocalDate cohortEnd = today.minusDays(8);
        LocalDate cohortStart = cohortEnd.minusDays(days - 1L);
        LocalDate revisitStart = cohortStart.isBefore(ACCESS_TRACKING_START) ? ACCESS_TRACKING_START : cohortStart;
        long classificationEligible = jdbcTemplate.queryForObject("""
                SELECT COUNT(*) FROM users
                WHERE (issuer IS NULL OR issuer NOT IN ('local-test', 'marketry-system'))
                  AND created_at >= ? AND created_at < ?
                """, Long.class, cohortStart.atStartOfDay(), cohortEnd.plusDays(1).atStartOfDay());
        long revisitEligible = jdbcTemplate.queryForObject("""
                SELECT COUNT(*) FROM users
                WHERE (issuer IS NULL OR issuer NOT IN ('local-test', 'marketry-system'))
                  AND created_at >= ? AND created_at < ?
                """, Long.class, revisitStart.atStartOfDay(), cohortEnd.plusDays(1).atStartOfDay());
        long classified = jdbcTemplate.queryForObject("""
                SELECT COUNT(*) FROM users
                WHERE (issuer IS NULL OR issuer NOT IN ('local-test', 'marketry-system'))
                  AND created_at >= ? AND created_at < ?
                  AND first_custom_classification_at IS NOT NULL
                  AND first_custom_classification_at <= created_at + INTERVAL '7 days'
                """, Long.class, cohortStart.atStartOfDay(), cohortEnd.plusDays(1).atStartOfDay());
        long returning = jdbcTemplate.queryForObject("""
                SELECT COUNT(DISTINCT u.id)
                FROM users u JOIN user_access_daily a ON a.user_id = u.id
                WHERE (u.issuer IS NULL OR u.issuer NOT IN ('local-test', 'marketry-system'))
                  AND u.created_at >= ? AND u.created_at < ?
                  AND a.access_date > CAST(u.created_at AS DATE)
                  AND a.access_date <= CAST(u.created_at AS DATE) + 7
                """, Long.class, revisitStart.atStartOfDay(), cohortEnd.plusDays(1).atStartOfDay());
        var pages = jdbcTemplate.query("""
                SELECT page,
                  COALESCE(SUM(event_count) FILTER (WHERE metric_type = 'PAGE_LOAD'), 0) AS page_loads,
                  COALESCE(SUM(total_duration_ms) FILTER (WHERE metric_type = 'PAGE_LOAD'), 0) AS load_ms,
                  COALESCE(SUM(event_count) FILTER (WHERE metric_type = 'CLIENT_ERROR'), 0) AS client_errors
                FROM admin_client_metrics_daily WHERE metric_date >= ? AND metric_date <= ?
                GROUP BY page ORDER BY page
                """, (rs, rowNum) -> {
            long loads = rs.getLong("page_loads");
            return new AdminPageMetricResponse(
                    rs.getString("page"), loads, loads == 0 ? 0 : rs.getLong("load_ms") / loads, rs.getLong("client_errors"));
        }, today.minusDays(days - 1L), today);
        Long[] captureTotals = jdbcTemplate.queryForObject("""
                SELECT COALESCE(SUM(event_count), 0), COALESCE(SUM(total_duration_ms), 0)
                FROM admin_client_metrics_daily WHERE metric_type = 'MAP_MOBILE_CAPTURE' AND metric_date >= ? AND metric_date <= ?
                """, (rs, rowNum) -> new Long[] {rs.getLong(1), rs.getLong(2)}, today.minusDays(days - 1L), today);
        long mobileCaptures = captureTotals[0];
        return new AdminDashboardMetricResponse(
                classificationEligible,
                classified,
                revisitEligible,
                returning,
                pages,
                mobileCaptures,
                mobileCaptures == 0 ? 0 : captureTotals[1] / mobileCaptures);
    }

    public void purgeBefore(LocalDate before) {
        jdbcTemplate.update("DELETE FROM admin_client_metrics_daily WHERE metric_date < ?", before);
    }
}
