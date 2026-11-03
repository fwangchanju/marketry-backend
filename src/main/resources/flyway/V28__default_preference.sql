CREATE TABLE default_preference (
    id BIGINT PRIMARY KEY,
    payload JSON NOT NULL,
    updated_at TIMESTAMP NOT NULL
);
