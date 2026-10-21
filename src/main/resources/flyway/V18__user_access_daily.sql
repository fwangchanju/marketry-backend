-- 회원별·날짜별 접속 기록. 하루에 한 줄이며, 그날 처음·마지막으로 접속한 시각과 기록된 횟수를 둔다.
-- 기록은 로그인·세션 갱신·세션 확인에서 남기고(회원당 5분에 한 번), 접속한 날 수와 일별 접속 회원 수를 세는 데 쓴다.
CREATE TABLE user_access_daily (
    user_id         BIGINT    NOT NULL,
    access_date     DATE      NOT NULL,
    first_access_at TIMESTAMP NOT NULL,
    last_access_at  TIMESTAMP NOT NULL,
    access_count    INTEGER   NOT NULL DEFAULT 1,
    CONSTRAINT pk_user_access_daily PRIMARY KEY (user_id, access_date),
    CONSTRAINT fk_user_access_daily_user FOREIGN KEY (user_id) REFERENCES users (id) DEFERRABLE INITIALLY DEFERRED
);
-- 일별 접속 회원 수를 날짜 범위로 셀 때 쓴다.
CREATE INDEX idx_user_access_daily_date ON user_access_daily (access_date);
