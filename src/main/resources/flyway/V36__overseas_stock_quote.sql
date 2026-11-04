CREATE TABLE overseas_stock_quote (
    market VARCHAR(16) NOT NULL,
    stock_code VARCHAR(16) NOT NULL,
    snapshot_time TIMESTAMP NOT NULL,
    current_price NUMERIC(14, 2) NOT NULL,
    last_price NUMERIC(14, 2) NOT NULL,
    change_rate NUMERIC(8, 2) NOT NULL,
    total_market_value NUMERIC(20, 0) NOT NULL,
    PRIMARY KEY (market, stock_code, snapshot_time)
);

CREATE INDEX idx_overseas_stock_quote_market_time ON overseas_stock_quote (market, snapshot_time);
