CREATE TABLE telegram_image_setting (
    id BIGINT PRIMARY KEY,
    payload JSONB NOT NULL,
    updated_at TIMESTAMP NOT NULL
);
