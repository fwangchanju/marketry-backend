-- 시스템 회원(MARKETRY 분류 공개본 계정)에 닉네임 MARKETRY를 줘서 다른 회원이 같은 닉네임(대소문자 무시)을 쓰지 못하게 한다.
-- 다른 회원이 이미 이 닉네임을 쓰고 있으면 넣지 않는다(고유 인덱스 충돌로 앱이 시작하지 못하는 일을 막는다).
INSERT INTO user_profile (user_id, nickname)
SELECT 900000, 'MARKETRY'
WHERE EXISTS (SELECT 1 FROM users WHERE id = 900000)
  AND NOT EXISTS (SELECT 1 FROM user_profile WHERE user_id = 900000)
  AND NOT EXISTS (SELECT 1 FROM user_profile WHERE lower(nickname) = 'marketry');
