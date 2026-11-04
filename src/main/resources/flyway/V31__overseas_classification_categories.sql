CREATE TABLE overseas_classification_category (
    id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    market VARCHAR(16) NOT NULL,
    parent_id BIGINT REFERENCES overseas_classification_category(id),
    name VARCHAR(12) NOT NULL
);

CREATE UNIQUE INDEX uk_overseas_classification_root
    ON overseas_classification_category (market, name) WHERE parent_id IS NULL;
CREATE UNIQUE INDEX uk_overseas_classification_child
    ON overseas_classification_category (market, parent_id, name) WHERE parent_id IS NOT NULL;

INSERT INTO overseas_classification_category (market, name)
SELECT DISTINCT market, sector_name
FROM overseas_stock_classification
WHERE sector_name IS NOT NULL AND TRIM(sector_name) <> '';

INSERT INTO overseas_classification_category (market, parent_id, name)
SELECT DISTINCT s.market, p.id, s.industry_name
FROM overseas_stock_classification s
JOIN overseas_classification_category p
  ON p.market = s.market AND p.name = s.sector_name AND p.parent_id IS NULL
WHERE s.industry_name IS NOT NULL AND TRIM(s.industry_name) <> '';

INSERT INTO overseas_classification_category (market, name)
SELECT 'JAPAN', '미분류'
WHERE NOT EXISTS (
    SELECT 1 FROM overseas_classification_category WHERE market = 'JAPAN' AND parent_id IS NULL AND name = '미분류'
);
