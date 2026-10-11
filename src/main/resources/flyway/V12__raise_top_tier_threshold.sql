-- Top 2 구간의 하한을 200조에서 500조로 올린다. 가입할 때 템플릿 사용자의 구간표를 복사하므로 템플릿 사용자와 모든 사용자의 행을 바꾼다.
-- 값을 직접 바꿔 둔 사용자(200조가 아닌 행)는 건드리지 않는다.
-- 되돌릴 때는 threshold_value를 500000000000000에서 200000000000000으로 바꾸는 마이그레이션을 새로 추가한다.
UPDATE custom_value_tier_threshold
SET threshold_value = 500000000000000,
    updated_at = CURRENT_TIMESTAMP
WHERE label = 'Top 2'
  AND threshold_value = 200000000000000;
