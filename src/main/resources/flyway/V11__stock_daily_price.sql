CREATE SEQUENCE stock_daily_price_id_seq START WITH 1 INCREMENT BY 500;

-- ka11081 원주가 일봉. stock_info FK 없이 이 테이블만 이관할 수 있게 한다.
CREATE TABLE stock_daily_price (
    id BIGINT PRIMARY KEY DEFAULT nextval('stock_daily_price_id_seq'),
    stock_code VARCHAR(20) NOT NULL,
    exchange_type VARCHAR(20) NOT NULL,
    trade_date DATE NOT NULL,
    open_price NUMERIC(19, 4) NOT NULL,
    high_price NUMERIC(19, 4) NOT NULL,
    low_price NUMERIC(19, 4) NOT NULL,
    close_price NUMERIC(19, 4) NOT NULL,
    volume BIGINT NOT NULL,
    created_at TIMESTAMP NOT NULL,
    CONSTRAINT uk_stock_daily_price UNIQUE (stock_code, exchange_type, trade_date),
    CONSTRAINT ck_stock_daily_price_exchange CHECK (exchange_type IN ('KRX', 'NXT')),
    CONSTRAINT ck_stock_daily_price_values CHECK (
        open_price >= 0 AND high_price >= 0 AND low_price >= 0 AND close_price >= 0 AND volume >= 0
    )
);

-- 테이블 단독 pg_dump에도 시퀀스와 현재 값이 포함되도록 연결한다.
ALTER SEQUENCE stock_daily_price_id_seq OWNED BY stock_daily_price.id;

CREATE INDEX idx_stock_daily_price_date ON stock_daily_price (trade_date, exchange_type);
