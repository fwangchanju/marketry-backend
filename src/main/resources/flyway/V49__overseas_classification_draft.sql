-- 일본·미국 "내 기준"(관리자 한 명이 운영 분류와 따로 고치는 초안)과, MARKETRY에 반영하기 직전 운영 분류의 백업(롤백용)을 저장할 새 표 세 개.
-- 새 표만 만들어서 기존 데이터와 옛 앱에 영향이 없다. 이 마이그레이션을 먼저 배포한 뒤 초안·반영 기능을 올린다.
-- 되돌릴 때는 이 표들을 지우는 마이그레이션을 새로 추가한다.

-- 초안 업종. 운영 업종(overseas_classification_category)과 같은 모양이고, live_id는 이 초안 업종이 복사해 온 운영 업종의 id다.
-- 반영할 때 live_id가 있는 업종은 운영의 같은 id 행을 고쳐서 사용자 설정(제외한 업종 id 등)이 깨지지 않게 하고, live_id가 없는(새로 만든) 업종만 운영에 새로 만든다.
CREATE TABLE overseas_classification_category_draft (
    id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    market VARCHAR(16) NOT NULL,
    live_id BIGINT,
    parent_id BIGINT REFERENCES overseas_classification_category_draft(id),
    name VARCHAR(40) NOT NULL
);

CREATE UNIQUE INDEX uk_overseas_category_draft_root
    ON overseas_classification_category_draft (market, name) WHERE parent_id IS NULL;
CREATE UNIQUE INDEX uk_overseas_category_draft_child
    ON overseas_classification_category_draft (market, parent_id, name) WHERE parent_id IS NOT NULL;
CREATE UNIQUE INDEX uk_overseas_category_draft_live
    ON overseas_classification_category_draft (market, live_id) WHERE live_id IS NOT NULL;

-- 초안 종목 분류. 운영 종목 분류(overseas_stock_classification)와 같은 모양이다.
CREATE TABLE overseas_stock_classification_draft (
    market VARCHAR(16) NOT NULL,
    stock_code VARCHAR(16) NOT NULL,
    sector_name VARCHAR(40),
    industry_name VARCHAR(40),
    sub_industry_name VARCHAR(40),
    PRIMARY KEY (market, stock_code)
);

-- 반영 직전 운영 분류 전체(업종·종목 분류)를 JSON 글로 적어 둔 백업. 시장마다 가장 최근 한 번분만 남긴다.
CREATE TABLE overseas_classification_backup (
    market VARCHAR(16) PRIMARY KEY,
    payload TEXT NOT NULL,
    created_at TIMESTAMP NOT NULL
);
