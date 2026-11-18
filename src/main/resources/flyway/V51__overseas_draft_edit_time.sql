-- 일본·미국 내 기준(초안)을 마지막으로 고친 시각. 설정창 맨 아래 "업데이트" 시각을 내 기준 모드에서 보여 주는 데 쓴다.
-- 새 표 하나만 만들어서 기존 데이터와 옛 앱에 영향이 없다. 이 마이그레이션을 먼저 배포한 뒤 시각을 기록하는 코드를 올린다.
-- 시장마다 한 줄만 둔다. 되돌릴 때는 이 표를 지우는 마이그레이션을 새로 추가한다.
CREATE TABLE overseas_classification_draft_edit (
    market VARCHAR(16) PRIMARY KEY,
    edited_at TIMESTAMP NOT NULL
);
