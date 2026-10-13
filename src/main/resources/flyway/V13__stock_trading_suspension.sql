CREATE SEQUENCE stock_trading_suspension_id_seq START WITH 1 INCREMENT BY 50;

-- 거래정지가 이어진 기간. 거래정지가 연속 5거래일에 이른 날부터 재개된 날 전까지(excluded_from ≤ 날짜 < ended_on)는 지도와 평균에서 뺀다.
-- 거래정지가 풀린 날 ended_on을 채우고, 같은 종목이 다시 정지되면 새 행을 만든다. 지난 날짜를 볼 때도 그날 기준으로 제외 여부를 판단하려고 이력을 남긴다.
CREATE TABLE stock_trading_suspension (
    id BIGINT PRIMARY KEY DEFAULT nextval('stock_trading_suspension_id_seq'),
    stock_code VARCHAR(20) NOT NULL,
    started_on DATE NOT NULL,
    excluded_from DATE,
    ended_on DATE,
    created_at TIMESTAMP NOT NULL,
    updated_at TIMESTAMP NOT NULL
);

ALTER SEQUENCE stock_trading_suspension_id_seq OWNED BY stock_trading_suspension.id;

CREATE INDEX idx_stock_trading_suspension_stock ON stock_trading_suspension (stock_code, started_on DESC);
CREATE INDEX idx_stock_trading_suspension_open ON stock_trading_suspension (ended_on);
