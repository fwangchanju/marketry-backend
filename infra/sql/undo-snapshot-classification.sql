-- copy-snapshot-classification.sql을 되돌린다. 복사한 일본·미국 자료를 지우고 풀었던 제약 두 개를 다시 건다.
-- 화면이 새 표를 쓰기 시작한 뒤에는 쓰지 않는다(그 뒤에 고친 내용이 사라진다). 한국 자료와 옛 표는 건드리지 않는다.
-- 실행: psql -v ON_ERROR_STOP=1 -v published_user_id=900000 -f undo-snapshot-classification.sql
\set ON_ERROR_STOP on
BEGIN;
SET LOCAL lock_timeout = '10s';
SELECT set_config('copy.published', :'published_user_id', true);
LOCK TABLE custom_sector, custom_stock_sector, custom_snapshot IN SHARE ROW EXCLUSIVE MODE;
DO $$
BEGIN
    IF EXISTS (SELECT 1 FROM custom_sector WHERE market <> 'KOREA'
               AND user_id NOT IN (SELECT id FROM users WHERE role = 'ADMIN' OR id = current_setting('copy.published')::BIGINT)) THEN
        RAISE EXCEPTION '다른 회원의 일본·미국 자료가 있어 되돌리지 않는다';
    END IF;
END $$;
DELETE FROM custom_stock_sector WHERE market <> 'KOREA';
DELETE FROM custom_sector WHERE market <> 'KOREA';
DELETE FROM custom_snapshot WHERE market <> 'KOREA';
SET CONSTRAINTS ALL IMMEDIATE;
ALTER TABLE custom_sector ADD CONSTRAINT uk_custom_sector_user_name UNIQUE (user_id, name);
ALTER TABLE custom_stock_sector ADD CONSTRAINT fk_custom_stock_sector_stock FOREIGN KEY (stock_code) REFERENCES stock_info (stock_code);
COMMIT;
