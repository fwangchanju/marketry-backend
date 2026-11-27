ALTER TABLE admin_client_metrics_daily DROP CONSTRAINT ck_admin_client_metrics_page;
ALTER TABLE admin_client_metrics_daily
    ADD CONSTRAINT ck_admin_client_metrics_page CHECK (page IN ('HOME', 'MAP', 'GRAPH', 'CUSTOM', 'CHART'));

ALTER TABLE admin_client_metrics_daily DROP CONSTRAINT ck_admin_client_metrics_type;
ALTER TABLE admin_client_metrics_daily
    ADD CONSTRAINT ck_admin_client_metrics_type CHECK (metric_type IN ('PAGE_LOAD', 'CLIENT_ERROR', 'MAP_MOBILE_CAPTURE', 'PC_CAPTURE'));
