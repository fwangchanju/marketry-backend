-- 첫 종목 분류 시각은 이후 배정을 지워도 최초 완료 통계를 유지한다.
ALTER TABLE users ADD COLUMN first_custom_classification_at TIMESTAMP;
UPDATE users u
SET first_custom_classification_at = assignments.first_assigned_at
FROM (
    SELECT assignment.user_id, MIN(assignment.created_at) AS first_assigned_at
    FROM custom_stock_sector assignment
    JOIN custom_sector sector ON sector.id = assignment.sector_id
    WHERE sector.name <> '신규 상장'
    GROUP BY assignment.user_id
) assignments
WHERE u.id = assignments.user_id;

-- 사용자 식별값 없이 일·화면·측정 종류별 집계만 보관한다.
CREATE TABLE admin_client_metrics_daily (
    metric_date DATE NOT NULL,
    page VARCHAR(20) NOT NULL,
    metric_type VARCHAR(20) NOT NULL,
    event_count BIGINT NOT NULL DEFAULT 0,
    total_duration_ms BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT pk_admin_client_metrics_daily PRIMARY KEY (metric_date, page, metric_type),
    CONSTRAINT ck_admin_client_metrics_page CHECK (page IN ('HOME', 'MAP', 'GRAPH', 'CUSTOM')),
    CONSTRAINT ck_admin_client_metrics_type CHECK (metric_type IN ('PAGE_LOAD', 'CLIENT_ERROR', 'MAP_MOBILE_CAPTURE'))
);
