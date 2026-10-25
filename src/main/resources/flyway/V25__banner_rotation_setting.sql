CREATE TABLE banner_rotation_setting (
    id BIGINT PRIMARY KEY CHECK (id = 1),
    interval_seconds INTEGER NOT NULL CHECK (interval_seconds IN (5, 10, 15, 30, 60)),
    updated_at TIMESTAMP NOT NULL
);
