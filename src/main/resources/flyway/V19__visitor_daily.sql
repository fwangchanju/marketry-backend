-- 비회원 방문자 일별 집계. 하루에 방문자 한 명당 한 줄이다.
-- visitor_key는 그날의 접속 IP·브라우저 정보를 서버 비밀값으로 되돌릴 수 없게 변환한 값이다(날짜가 섞여 있어 다른 날과 연결되지 않는다).
-- 일별 방문자 수와 회원 가입 전환율을 세는 데만 쓰고, 오래된 행은 정리 배치가 지운다.
CREATE TABLE visitor_daily (
    visit_date  DATE     NOT NULL,
    visitor_key VARCHAR(64) NOT NULL,
    CONSTRAINT pk_visitor_daily PRIMARY KEY (visit_date, visitor_key)
);
