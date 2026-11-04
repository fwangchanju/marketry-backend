-- 부동산은 대분류와 중분류가 같은 뜻이라 중분류를 빼고 소분류를 중분류로 올린다.
-- 비는 중분류(부동산 종합)는 마이그레이션에서 지우지 않고 J-SECTOR 화면에서 지운다.
UPDATE overseas_stock_classification
SET industry_name = sub_industry_name, sub_industry_name = NULL
WHERE market = 'JAPAN' AND sector_name = '부동산' AND industry_name = '부동산 종합' AND sub_industry_name IS NOT NULL;

UPDATE overseas_classification_category c
SET parent_id = root.id
FROM overseas_classification_category middle
JOIN overseas_classification_category root
  ON root.id = middle.parent_id AND root.parent_id IS NULL AND root.market = 'JAPAN' AND root.name = '부동산'
WHERE c.parent_id = middle.id AND middle.name = '부동산 종합';
