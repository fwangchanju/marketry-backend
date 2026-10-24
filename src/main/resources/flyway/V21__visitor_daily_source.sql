-- 비회원 방문자가 어디에서 들어왔는지 구분하는 칸. 출처는 NAVER·GOOGLE·DIRECT·OTHER 네 가지로만 저장하고 주소 자체는 저장하지 않는다.
-- 기록을 시작하기 전의 행은 NULL로 두고, 유입 경로 집계에서 뺀다.
ALTER TABLE visitor_daily ADD COLUMN source VARCHAR(20);
