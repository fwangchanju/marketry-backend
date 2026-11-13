-- 일본 종목 이름 2개와 분류 3건을 정한 값으로 맞춘다. 로컬에서 맞춘 값이 운영에 빠져 있었다(2026-11-14).
-- 이미 정한 값이거나 사용자가 다른 값으로 고쳤다면 조건이 맞지 않아 그대로 남는다(덮어쓰지 않는다).

-- 이름: 고친 종목만 행이 있고 행이 없으면 기본 이름이 나온다. 행이 없을 때만 넣는다.
INSERT INTO japan_stock_korean_name (stock_code, korean_name) VALUES
    ('4506', '스미토모제약'),
    ('1332', '닛스이')
ON CONFLICT (stock_code) DO NOTHING;

-- 분류: 콤시스홀딩스(1721)·JGC홀딩스(1963)는 산업재 > 주택·건설의 소분류가 비어 있다. 종합 건설로 둔다.
UPDATE overseas_stock_classification
SET sub_industry_name = '종합 건설'
WHERE market = 'JAPAN'
  AND stock_code IN ('1721', '1963')
  AND sector_name = '산업재'
  AND industry_name = '주택·건설'
  AND sub_industry_name IS NULL;

-- 다이와 하우스 공업(1925)은 부동산(중·소분류 없음)에서 산업재 > 주택·건설 > 주택으로 옮긴다.
UPDATE overseas_stock_classification
SET sector_name = '산업재', industry_name = '주택·건설', sub_industry_name = '주택'
WHERE market = 'JAPAN'
  AND stock_code = '1925'
  AND sector_name = '부동산'
  AND industry_name IS NULL
  AND sub_industry_name IS NULL;
