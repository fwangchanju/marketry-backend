ALTER TABLE banner_campaign
    ADD COLUMN text_scale_percent INTEGER NOT NULL DEFAULT 100
        CHECK (text_scale_percent IN (80, 100, 120));

ALTER TABLE banner_campaign
    ADD COLUMN text_bold BOOLEAN NOT NULL DEFAULT FALSE;
