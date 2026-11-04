-- 지도가 분류를 이름으로 찾으므로 이름은 시장 안에서 하나뿐이어야 한다. 겹치는 중분류 2개의 이름을 바꾼다.
UPDATE overseas_stock_classification
SET industry_name = '식품·할인점'
WHERE market = 'JAPAN' AND sector_name = '필수 소비재' AND industry_name = '소매';

UPDATE overseas_stock_classification
SET industry_name = '부동산 종합'
WHERE market = 'JAPAN' AND sector_name = '부동산' AND industry_name = '부동산';

UPDATE overseas_classification_category c
SET name = '식품·할인점'
FROM overseas_classification_category p
WHERE c.parent_id = p.id AND p.parent_id IS NULL AND p.market = 'JAPAN' AND p.name = '필수 소비재' AND c.name = '소매';

UPDATE overseas_classification_category c
SET name = '부동산 종합'
FROM overseas_classification_category p
WHERE c.parent_id = p.id AND p.parent_id IS NULL AND p.market = 'JAPAN' AND p.name = '부동산' AND c.name = '부동산';
