-- 해외 종목의 날짜별 일봉(시가·고가·저가·종가·거래량). 5분 단위 시세(overseas_stock_quote)에는 종가 기록만 있어 종목 차트를 그릴 수 없었다.
CREATE TABLE overseas_stock_daily (
    market VARCHAR(16) NOT NULL,
    stock_code VARCHAR(16) NOT NULL,
    trade_date DATE NOT NULL,
    open_price NUMERIC(14, 2) NOT NULL,
    high_price NUMERIC(14, 2) NOT NULL,
    low_price NUMERIC(14, 2) NOT NULL,
    close_price NUMERIC(14, 2) NOT NULL,
    volume BIGINT NOT NULL,
    created_at TIMESTAMP NOT NULL,
    PRIMARY KEY (market, stock_code, trade_date)
);
