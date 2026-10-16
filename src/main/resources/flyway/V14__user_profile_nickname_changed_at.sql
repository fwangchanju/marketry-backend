-- 닉네임을 마지막으로 바꾼 시각. 첫 등록(닉네임이 없던 상태)은 기록하지 않고, 그 뒤 바꿀 때부터 기록해 교체 간격 제한에 쓴다.
ALTER TABLE user_profile ADD COLUMN nickname_changed_at TIMESTAMP;
