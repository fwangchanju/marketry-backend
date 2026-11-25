-- 일본·미국 분류(overseas_*)를 한국과 같은 표(custom_*)로 복사한다. 소유자가 DB에서 직접 실행한다(Flyway가 실행하지 않는다).
--   1) 일본·미국 업종 이름과 종목을 담을 수 있게 옛 제약 두 개를 푼다.
--   2) MARKETRY(운영 분류) → 발행 계정, 내 기준(초안) → 관리자 계정으로 복사하고 발행 계정에 첫 버전을 만든다.
--   3) 원본과 하나라도 다르면 전부 취소한다(한 트랜잭션).
-- 옛 표와 한국 자료는 건드리지 않는다. 같은 업종의 고정 이름표(stable_key)는 다시 실행해도 같다.
-- 실행: psql -v ON_ERROR_STOP=1 -v published_user_id=900000 -v refresh=0 -f copy-snapshot-classification.sql
--   refresh=1은 이미 복사한 일본·미국 자료를 지우고 다시 복사한다. 화면이 새 표를 쓰기 시작한 뒤에는 쓰지 않는다.
--   끝 줄을 ROLLBACK으로 바꿔 실행하면 아무것도 저장하지 않고 시험만 한다.
\set ON_ERROR_STOP on
BEGIN;
SET LOCAL lock_timeout = '10s';
SELECT set_config('copy.published', :'published_user_id', true), set_config('copy.refresh', :'refresh', true);
LOCK TABLE overseas_classification_category, overseas_stock_classification, overseas_classification_category_draft,
    overseas_stock_classification_draft, custom_sector, custom_stock_sector, custom_snapshot IN SHARE ROW EXCLUSIVE MODE;

DO $$
DECLARE
    published BIGINT := current_setting('copy.published')::BIGINT;
    admins INT;
BEGIN
    SELECT count(*) INTO admins FROM users WHERE role = 'ADMIN' AND id <> published;
    IF admins <> 1 THEN RAISE EXCEPTION '관리자 계정이 하나가 아니다: %', admins; END IF;
    IF NOT EXISTS (SELECT 1 FROM users WHERE id = published) THEN RAISE EXCEPTION '발행 계정 %이 없다', published; END IF;
    PERFORM set_config('copy.admin', (SELECT id FROM users WHERE role = 'ADMIN' AND id <> published)::TEXT, true);
    IF EXISTS (SELECT 1 FROM custom_sector WHERE market <> 'KOREA'
               AND user_id NOT IN (published, current_setting('copy.admin')::BIGINT)) THEN
        RAISE EXCEPTION '다른 회원의 일본·미국 자료가 있다. 이미 공개된 뒤라 복사하지 않는다';
    END IF;
    IF current_setting('copy.refresh') <> '1' AND (
        EXISTS (SELECT 1 FROM custom_sector WHERE market <> 'KOREA')
        OR EXISTS (SELECT 1 FROM custom_stock_sector WHERE market <> 'KOREA')
        OR EXISTS (SELECT 1 FROM custom_snapshot WHERE market <> 'KOREA')) THEN
        RAISE EXCEPTION '이미 복사한 자료가 있다. 다시 복사하려면 refresh=1';
    END IF;
    IF EXISTS (SELECT 1 FROM overseas_stock_classification s JOIN custom_stock_sector k ON k.stock_code = s.stock_code
               WHERE k.market = 'KOREA') THEN RAISE EXCEPTION '한국과 겹치는 종목 코드가 있다'; END IF;
END $$;

CREATE TEMP TABLE korea_before ON COMMIT DROP AS
SELECT (SELECT md5(coalesce(string_agg(x::TEXT, '|' ORDER BY x.id), '')) FROM custom_sector x WHERE market = 'KOREA') AS sectors,
       (SELECT md5(coalesce(string_agg(x::TEXT, '|' ORDER BY x.user_id, x.stock_code), '')) FROM custom_stock_sector x WHERE market = 'KOREA') AS stocks,
       (SELECT md5(coalesce(string_agg(x::TEXT, '|' ORDER BY x.id), '')) FROM custom_snapshot x WHERE market = 'KOREA') AS snapshots;

ALTER TABLE custom_sector DROP CONSTRAINT IF EXISTS uk_custom_sector_user_name;
ALTER TABLE custom_stock_sector DROP CONSTRAINT IF EXISTS fk_custom_stock_sector_stock;

-- refresh=1일 때만 지울 것이 있다. 위에서 다른 회원의 자료가 없음을 확인했다.
DELETE FROM custom_stock_sector WHERE market <> 'KOREA';
DELETE FROM custom_sector WHERE market <> 'KOREA';
DELETE FROM custom_snapshot WHERE market <> 'KOREA';

-- 업종 — 운영 분류와 초안을 한 모양으로 모은다. 초안은 운영에서 온 업종이면 같은 고정 이름표를 쓴다.
CREATE TEMP TABLE source_sector ON COMMIT DROP AS
WITH RECURSIVE sources AS (
    SELECT current_setting('copy.published')::BIGINT AS user_id, market, id, parent_id, name,
           md5(market || ':live:' || id)::UUID AS stable_key
    FROM overseas_classification_category
    UNION ALL
    SELECT current_setting('copy.admin')::BIGINT, market, id, parent_id, name,
           CASE WHEN live_id IS NULL THEN md5(market || ':draft:' || id) ELSE md5(market || ':live:' || live_id) END::UUID
    FROM overseas_classification_category_draft
), tree AS (
    SELECT s.*, 0 AS depth, ARRAY[s.name::TEXT] AS path FROM sources s WHERE parent_id IS NULL
    UNION ALL
    SELECT s.*, t.depth + 1, t.path || s.name::TEXT FROM sources s JOIN tree t ON t.user_id = s.user_id AND t.id = s.parent_id
)
SELECT tree.*, coalesce(CASE WHEN tree.user_id = current_setting('copy.published')::BIGINT THEN p.published_at ELSE e.edited_at END,
                        CURRENT_TIMESTAMP::TIMESTAMP) AS changed_at
FROM tree
LEFT JOIN overseas_classification_publication p ON p.market = tree.market
LEFT JOIN overseas_classification_draft_edit e ON e.market = tree.market;

CREATE TEMP TABLE source_stock ON COMMIT DROP AS
SELECT current_setting('copy.published')::BIGINT AS user_id, market, stock_code,
       array_remove(ARRAY[sector_name, industry_name, sub_industry_name]::TEXT[], NULL) AS path
FROM overseas_stock_classification
UNION ALL
SELECT current_setting('copy.admin')::BIGINT, market, stock_code,
       array_remove(ARRAY[sector_name, industry_name, sub_industry_name]::TEXT[], NULL)
FROM overseas_stock_classification_draft;

-- 첫 버전의 이름·시각은 그 시장의 마지막 업데이트 시각이다. 내용은 복사가 끝난 뒤 채운다.
INSERT INTO custom_snapshot (user_id, market, label, snapshot_json, created_at, updated_at)
SELECT DISTINCT user_id, market, 'MARKETRY ' || to_char(changed_at, 'YYYY-MM-DD HH24:MI'), '{}'::JSONB, changed_at, changed_at
FROM source_sector WHERE user_id = current_setting('copy.published')::BIGINT;

INSERT INTO custom_sector (user_id, market, parent_id, snapshot_id, name, depth, is_excluded, stable_key, created_at, updated_at)
SELECT s.user_id, s.market, NULL, n.id, s.name, s.depth, FALSE, s.stable_key, s.changed_at, s.changed_at
FROM source_sector s
LEFT JOIN custom_snapshot n ON n.user_id = s.user_id AND n.market = s.market
ORDER BY s.user_id, s.market, s.depth, s.id;

UPDATE custom_sector child
SET parent_id = parent.id
FROM source_sector s
JOIN source_sector sp ON sp.user_id = s.user_id AND sp.id = s.parent_id
JOIN custom_sector parent ON parent.user_id = sp.user_id AND parent.market = sp.market AND parent.stable_key = sp.stable_key
WHERE child.user_id = s.user_id AND child.market = s.market AND child.stable_key = s.stable_key;

-- 관리자 내 기준의 제외 업종(계정 설정의 초안 업종 번호)을 서버 업종으로 옮긴다.
UPDATE custom_sector target
SET is_excluded = TRUE
FROM user_preference pref
CROSS JOIN LATERAL (VALUES ('JAPAN', 'japan.map.excludedSectorIds'), ('USA', 'usa.map.excludedSectorIds')) AS k(market, key)
CROSS JOIN LATERAL jsonb_array_elements_text(
    CASE WHEN jsonb_typeof(pref.payload -> k.key) = 'array' THEN pref.payload -> k.key ELSE '[]'::JSONB END) AS excluded(id)
JOIN source_sector s ON s.market = k.market AND s.id::TEXT = excluded.id AND s.user_id = current_setting('copy.admin')::BIGINT
WHERE pref.user_id = current_setting('copy.admin')::BIGINT
  AND target.user_id = s.user_id AND target.market = s.market AND target.stable_key = s.stable_key;

INSERT INTO custom_stock_sector (user_id, market, stock_code, sector_id, created_at, updated_at)
SELECT k.user_id, k.market, k.stock_code, t.id, s.changed_at, s.changed_at
FROM source_stock k
JOIN source_sector s ON s.user_id = k.user_id AND s.market = k.market AND s.path = k.path
JOIN custom_sector t ON t.user_id = s.user_id AND t.market = s.market AND t.stable_key = s.stable_key;

-- 첫 버전 — 일본·미국 버전에는 업종과 종목 배정만 담는다.
UPDATE custom_snapshot n
SET snapshot_json = jsonb_build_object(
    'snapshotVersion', 3,
    'sectors', (SELECT jsonb_agg(jsonb_build_object('id', c.id, 'parentId', c.parent_id, 'name', c.name, 'depth', c.depth,
                                                    'excluded', c.is_excluded, 'stableKey', c.stable_key) ORDER BY c.id)
                FROM custom_sector c WHERE c.user_id = n.user_id AND c.market = n.market),
    'assignments', (SELECT jsonb_agg(jsonb_build_object('stockCode', a.stock_code, 'sectorId', a.sector_id) ORDER BY a.stock_code)
                    FROM custom_stock_sector a WHERE a.user_id = n.user_id AND a.market = n.market),
    'aliases', '[]'::JSONB, 'scaleThresholds', '[]'::JSONB, 'valueTierThresholds', '[]'::JSONB, 'preferences', '{}'::JSONB)
WHERE n.market <> 'KOREA';

-- 원본과 전부 맞춰 본다. 하나라도 다르면 취소한다.
CREATE TEMP TABLE copied_sector ON COMMIT DROP AS
WITH RECURSIVE tree AS (
    SELECT id, user_id, market, name, depth, stable_key, 0 AS level, ARRAY[name::TEXT] AS path
    FROM custom_sector WHERE market <> 'KOREA' AND parent_id IS NULL
    UNION ALL
    SELECT c.id, c.user_id, c.market, c.name, c.depth, c.stable_key, t.level + 1, t.path || c.name::TEXT
    FROM custom_sector c JOIN tree t ON c.parent_id = t.id AND c.market = t.market
) SELECT * FROM tree;

DO $$
DECLARE n INT;
BEGIN
    SELECT count(*) INTO n FROM (
        (SELECT user_id, market, path, depth, stable_key FROM source_sector EXCEPT SELECT user_id, market, path, level, stable_key FROM copied_sector)
        UNION ALL
        (SELECT user_id, market, path, level, stable_key FROM copied_sector EXCEPT SELECT user_id, market, path, depth, stable_key FROM source_sector)) d;
    IF n <> 0 THEN RAISE EXCEPTION '업종이 원본과 다르다: %건', n; END IF;
    IF (SELECT count(*) FROM copied_sector) <> (SELECT count(*) FROM overseas_classification_category) + (SELECT count(*) FROM overseas_classification_category_draft)
        OR (SELECT count(*) FROM copied_sector) <> (SELECT count(*) FROM custom_sector WHERE market <> 'KOREA')
        OR EXISTS (SELECT 1 FROM copied_sector WHERE depth <> level) THEN RAISE EXCEPTION '업종 수나 단계가 원본과 다르다'; END IF;
    SELECT count(*) INTO n FROM (
        (SELECT user_id, market, stock_code, path FROM source_stock EXCEPT
         SELECT a.user_id, a.market, a.stock_code, c.path FROM custom_stock_sector a JOIN copied_sector c ON c.id = a.sector_id AND c.market = a.market)
        UNION ALL
        (SELECT a.user_id, a.market, a.stock_code, c.path FROM custom_stock_sector a JOIN copied_sector c ON c.id = a.sector_id AND c.market = a.market
         EXCEPT SELECT user_id, market, stock_code, path FROM source_stock)) d;
    IF n <> 0 THEN RAISE EXCEPTION '종목 배정이 원본과 다르다: %건', n; END IF;
    IF EXISTS (SELECT 1 FROM custom_stock_sector a JOIN custom_sector s ON s.id = a.sector_id WHERE s.market <> a.market)
        THEN RAISE EXCEPTION '시장이 섞인 배정이 있다'; END IF;
    IF (SELECT count(*) FROM custom_sector WHERE market <> 'KOREA' AND name = '미분류' AND parent_id IS NULL)
        <> (SELECT count(*) FROM (SELECT DISTINCT user_id, market FROM source_sector) t) THEN RAISE EXCEPTION '미분류 업종이 빠졌다'; END IF;
    IF (SELECT count(*) FROM korea_before b
        WHERE b.sectors = (SELECT md5(coalesce(string_agg(x::TEXT, '|' ORDER BY x.id), '')) FROM custom_sector x WHERE market = 'KOREA')
          AND b.stocks = (SELECT md5(coalesce(string_agg(x::TEXT, '|' ORDER BY x.user_id, x.stock_code), '')) FROM custom_stock_sector x WHERE market = 'KOREA')
          AND b.snapshots = (SELECT md5(coalesce(string_agg(x::TEXT, '|' ORDER BY x.id), '')) FROM custom_snapshot x WHERE market = 'KOREA')) <> 1
        THEN RAISE EXCEPTION '한국 자료가 바뀌었다'; END IF;
END $$;

SELECT s.market AS 시장, CASE WHEN s.user_id = current_setting('copy.published')::BIGINT THEN 'MARKETRY' ELSE '내 기준' END AS 구분,
       count(*) AS 업종, (SELECT count(*) FROM custom_stock_sector a WHERE a.user_id = s.user_id AND a.market = s.market) AS 종목,
       count(*) FILTER (WHERE s.is_excluded) AS 제외,
       (SELECT count(*) FROM custom_snapshot n WHERE n.user_id = s.user_id AND n.market = s.market) AS 버전
FROM custom_sector s WHERE s.market <> 'KOREA' GROUP BY s.market, s.user_id ORDER BY 1, 2;
COMMIT;
