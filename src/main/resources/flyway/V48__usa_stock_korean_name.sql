-- 미국 종목의 한글 이름. 일본(japan_stock_korean_name)과 같은 모양의 새 표라 기존 데이터에 영향이 없다.
CREATE TABLE usa_stock_korean_name (
    symbol VARCHAR(16) PRIMARY KEY,
    korean_name VARCHAR(100) NOT NULL
);
