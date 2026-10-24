CREATE TABLE banner_campaign (
    id BIGSERIAL PRIMARY KEY,
    type VARCHAR(16) NOT NULL CHECK (type IN ('AD', 'NOTICE')),
    title VARCHAR(100) NOT NULL,
    text TEXT,
    link_url VARCHAR(1000),
    image BYTEA,
    image_content_type VARCHAR(32),
    starts_at TIMESTAMP NOT NULL,
    ends_at TIMESTAMP,
    approved_at TIMESTAMP,
    created_at TIMESTAMP NOT NULL,
    updated_at TIMESTAMP NOT NULL,
    CHECK (ends_at IS NULL OR ends_at > starts_at),
    CHECK ((image IS NULL) = (image_content_type IS NULL))
);

CREATE INDEX idx_banner_campaign_approved_schedule
    ON banner_campaign (starts_at, ends_at)
    WHERE approved_at IS NOT NULL;
