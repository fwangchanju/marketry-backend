-- 검색 로봇 방문 횟수(날짜·로봇 이름별). 사람 방문(visitor_daily)과 섞지 않고, IP나 해시는 남기지 않는다.
CREATE TABLE bot_visit_daily (
    visit_date DATE NOT NULL,
    bot_name VARCHAR(40) NOT NULL,
    visit_count INTEGER NOT NULL DEFAULT 0,
    CONSTRAINT pk_bot_visit_daily PRIMARY KEY (visit_date, bot_name)
);
