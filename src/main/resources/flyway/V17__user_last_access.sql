-- 회원이 마지막으로 접속한 시각. 로그인·세션 갱신·화면을 열 때의 세션 확인에서 기록하며, 기존 회원은 기록이 생길 때까지 비어 있다.
ALTER TABLE users ADD COLUMN last_access_at TIMESTAMP;
