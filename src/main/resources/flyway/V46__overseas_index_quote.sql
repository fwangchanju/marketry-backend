-- 해외 지수(닛케이 225)의 장중 값. 일본 5분 시세와 같은 시각으로 저장한다.
-- 오래된 날짜는 종목 시세와 같이 그날의 마지막 값만 남긴다.
CREATE TABLE overseas_index_quote (
    market VARCHAR(16) NOT NULL,
    snapshot_time TIMESTAMP NOT NULL,
    current_value NUMERIC(14, 2) NOT NULL,
    last_value NUMERIC(14, 2) NOT NULL,
    change_rate NUMERIC(8, 2) NOT NULL,
    PRIMARY KEY (market, snapshot_time)
);
