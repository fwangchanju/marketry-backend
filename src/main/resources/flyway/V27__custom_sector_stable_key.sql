ALTER TABLE custom_sector
    ADD COLUMN stable_key UUID NOT NULL DEFAULT gen_random_uuid();

CREATE UNIQUE INDEX uk_custom_sector_user_stable_key
    ON custom_sector (user_id, stable_key);
