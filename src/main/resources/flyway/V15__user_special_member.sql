-- 특별 회원 표시. 권한(role)은 그대로 두고 일반 회원(USER) 중 특별 대우하는 회원만 true로 둔다.
ALTER TABLE users ADD COLUMN special BOOLEAN NOT NULL DEFAULT FALSE;
