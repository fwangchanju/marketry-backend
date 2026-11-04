-- 중분류와 이름이 같은 소분류 3개의 이름을 바꾼다. 이미 고친 값은 조건이 맞지 않아 그대로 남는다.
UPDATE overseas_stock_classification s
SET sub_industry_name = v.new_name
FROM (VALUES
    ('기술', '반도체', '반도체', '반도체 제조'),
    ('기술', '전자 부품', '전자 부품', '일반 전자 부품'),
    ('기초 소재', '화학', '화학', '범용 화학')
) AS v(sector, middle, old_name, new_name)
WHERE s.market = 'JAPAN'
  AND s.sector_name = v.sector
  AND s.industry_name = v.middle
  AND s.sub_industry_name = v.old_name;

UPDATE overseas_classification_category c
SET name = v.new_name
FROM (VALUES
    ('기술', '반도체', '반도체', '반도체 제조'),
    ('기술', '전자 부품', '전자 부품', '일반 전자 부품'),
    ('기초 소재', '화학', '화학', '범용 화학')
) AS v(sector, middle, old_name, new_name)
JOIN overseas_classification_category p
  ON p.market = 'JAPAN' AND p.parent_id IS NULL AND p.name = v.sector
JOIN overseas_classification_category mid
  ON mid.parent_id = p.id AND mid.name = v.middle
WHERE c.parent_id = mid.id
  AND c.name = v.old_name;
