-- 과거 일봉을 분할까지 바르게 계산하려고 키움의 전일 대비와 수정 사유를 함께 저장한다. 기존 행은 비어 있다가 다시 받을 때 채운다.
ALTER TABLE stock_daily_price
    ADD COLUMN change_value NUMERIC(19, 4),
    ADD COLUMN adjust_type VARCHAR(10),
    ADD COLUMN adjust_rate NUMERIC(9, 4);

-- 지수의 날짜별 값. 5분 단위 시장 요약(market_overview_snapshot)과 달리 상승·하락 종목 수가 없는 과거까지 담는다.
CREATE TABLE market_index_daily (
    market VARCHAR(16) NOT NULL,
    trade_date DATE NOT NULL,
    open_value NUMERIC(14, 2) NOT NULL,
    high_value NUMERIC(14, 2) NOT NULL,
    low_value NUMERIC(14, 2) NOT NULL,
    close_value NUMERIC(14, 2) NOT NULL,
    volume BIGINT NOT NULL,
    trading_value BIGINT,
    created_at TIMESTAMP NOT NULL,
    PRIMARY KEY (market, trade_date)
);

-- 나눠 받는 과거 데이터 수집이 어디까지 끝났는지 기록한다. 중간에 멈춰도 끝난 종목은 다시 호출하지 않는다.
CREATE TABLE history_backfill_progress (
    job VARCHAR(32) NOT NULL,
    item VARCHAR(20) NOT NULL,
    saved_rows INT NOT NULL,
    oldest_date DATE,
    completed_at TIMESTAMP NOT NULL,
    PRIMARY KEY (job, item)
);
