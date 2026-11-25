-- 회원 분류(업종·종목 배정·버전)에 시장 칸을 더한다. 지금 있는 자료는 모두 한국이다. 일본·미국 분류를 같은 구조로 옮기기 위한 준비다.
-- 칸과 인덱스만 더하고 기본값이 있어, 시장을 모르는 옛 앱도 그대로 읽고 쓴다.
-- 기존 제약(uk_custom_sector_user_name, fk_custom_stock_sector_stock)은 그대로 둔다. 일본·미국 자료를 넣기 전에 따로 푼다.
ALTER TABLE custom_sector ADD COLUMN market VARCHAR(16) NOT NULL DEFAULT 'KOREA';
ALTER TABLE custom_stock_sector ADD COLUMN market VARCHAR(16) NOT NULL DEFAULT 'KOREA';
ALTER TABLE custom_snapshot ADD COLUMN market VARCHAR(16) NOT NULL DEFAULT 'KOREA';

CREATE UNIQUE INDEX uk_custom_sector_user_market_name ON custom_sector (user_id, market, name);
CREATE INDEX idx_custom_stock_sector_user_market ON custom_stock_sector (user_id, market);
CREATE INDEX idx_custom_snapshot_user_market ON custom_snapshot (user_id, market, id);
