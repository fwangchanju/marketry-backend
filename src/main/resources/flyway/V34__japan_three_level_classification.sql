-- 업종을 대·중·소분류로 나눈다. 값을 이미 고친 종목과 분류는 조건이 맞지 않아 그대로 남는다.

-- 복합 기업에서 상사가 아닌 종목을 옮긴다.
UPDATE overseas_stock_classification SET sector_name = '산업재', industry_name = '전기 장비', sub_industry_name = NULL
WHERE market = 'JAPAN' AND stock_code = '6501' AND sector_name = '산업재' AND industry_name = '복합 기업';
UPDATE overseas_stock_classification SET sector_name = '산업재', industry_name = '전기 장비', sub_industry_name = NULL
WHERE market = 'JAPAN' AND stock_code = '5803' AND sector_name = '산업재' AND industry_name = '복합 기업';
UPDATE overseas_stock_classification SET sector_name = '산업재', industry_name = '기계', sub_industry_name = '특수 산업 기계'
WHERE market = 'JAPAN' AND stock_code = '7012' AND sector_name = '산업재' AND industry_name = '복합 기업';
UPDATE overseas_stock_classification SET sector_name = '산업재', industry_name = '운송·물류', sub_industry_name = '철도'
WHERE market = 'JAPAN' AND stock_code = '9008' AND sector_name = '산업재' AND industry_name = '복합 기업';
UPDATE overseas_stock_classification SET sector_name = '산업재', industry_name = '기타 서비스', sub_industry_name = '인쇄'
WHERE market = 'JAPAN' AND stock_code = '7911' AND sector_name = '산업재' AND industry_name = '복합 기업';
UPDATE overseas_stock_classification SET sector_name = '산업재', industry_name = '기타 서비스', sub_industry_name = '인쇄'
WHERE market = 'JAPAN' AND stock_code = '7912' AND sector_name = '산업재' AND industry_name = '복합 기업';
UPDATE overseas_stock_classification SET sector_name = '기초 소재', industry_name = '화학', sub_industry_name = '화학'
WHERE market = 'JAPAN' AND stock_code = '3407' AND sector_name = '산업재' AND industry_name = '복합 기업';
UPDATE overseas_stock_classification SET sector_name = '기초 소재', industry_name = '화학', sub_industry_name = '화학'
WHERE market = 'JAPAN' AND stock_code = '3401' AND sector_name = '산업재' AND industry_name = '복합 기업';
UPDATE overseas_stock_classification SET sector_name = '기초 소재', industry_name = '화학', sub_industry_name = '화학'
WHERE market = 'JAPAN' AND stock_code = '5201' AND sector_name = '산업재' AND industry_name = '복합 기업';
UPDATE overseas_stock_classification SET sector_name = '기초 소재', industry_name = '금속', sub_industry_name = '기타 산업 금속·광업'
WHERE market = 'JAPAN' AND stock_code = '5706' AND sector_name = '산업재' AND industry_name = '복합 기업';
UPDATE overseas_stock_classification SET sector_name = '기술', industry_name = '전자 부품', sub_industry_name = '전자 부품'
WHERE market = 'JAPAN' AND stock_code = '6971' AND sector_name = '산업재' AND industry_name = '복합 기업';
UPDATE overseas_stock_classification SET sector_name = '기술', industry_name = '하드웨어', sub_industry_name = NULL
WHERE market = 'JAPAN' AND stock_code = '4901' AND sector_name = '산업재' AND industry_name = '복합 기업';
UPDATE overseas_stock_classification SET sector_name = '커뮤니케이션', industry_name = '게임', sub_industry_name = NULL
WHERE market = 'JAPAN' AND stock_code = '2432' AND sector_name = '산업재' AND industry_name = '복합 기업';

-- 나머지 종목은 지금 업종을 소분류로 내리거나 새 이름으로 바꾼다.
UPDATE overseas_stock_classification s
SET industry_name = v.middle, sub_industry_name = v.sub
FROM (VALUES
    ('산업재', '복합 기업', '상사', NULL),
    ('산업재', '철도', '운송·물류', '철도'),
    ('산업재', '해상 운송', '운송·물류', '해상 운송'),
    ('산업재', '항공', '운송·물류', '항공'),
    ('산업재', '트럭 운송', '운송·물류', '트럭 운송'),
    ('산업재', '종합 화물·물류', '운송·물류', '종합 화물·물류'),
    ('산업재', '특수 산업 기계', '기계', '특수 산업 기계'),
    ('산업재', '농업·건설 중장비', '기계', '농업·건설 중장비'),
    ('산업재', '공구·부속품', '기계', '공구·부속품'),
    ('산업재', '엔지니어링·건설', '건설', '엔지니어링·건설'),
    ('산업재', '건축 제품·장비', '건설', '건축 제품·장비'),
    ('산업재', '전기 장비·부품', '전기 장비', NULL),
    ('산업재', '컨설팅 서비스', '기타 서비스', '컨설팅 서비스'),
    ('산업재', '사무 장비·용품', '기타 서비스', '사무 장비·용품'),
    ('산업재', '보안·경비 서비스', '기타 서비스', '보안·경비 서비스'),
    ('경기 소비재', '자동차 제조', '자동차', '자동차 제조'),
    ('경기 소비재', '자동차 부품', '자동차', '자동차 부품'),
    ('경기 소비재', '백화점', '소매', '백화점'),
    ('경기 소비재', '인터넷 소매', '소매', '인터넷 소매'),
    ('경기 소비재', '의류 소매', '소매', '의류 소매'),
    ('경기 소비재', '가구·비품·가전', '소매', '가구·비품·가전'),
    ('경기 소비재', '레저', '레저', NULL),
    ('경기 소비재', '주택 건설', '기타 소비재', '주택 건설'),
    ('경기 소비재', '섬유 제조', '기타 소비재', '섬유 제조'),
    ('기술', '반도체', '반도체', '반도체'),
    ('기술', '반도체 장비·소재', '반도체', '반도체 장비·소재'),
    ('기술', '전자 부품', '전자 부품', '전자 부품'),
    ('기술', '과학·기술 기기', '전자 부품', '과학·기술 기기'),
    ('기술', '소비자 전자제품', '하드웨어', '소비자 전자제품'),
    ('기술', '컴퓨터 하드웨어', '하드웨어', '컴퓨터 하드웨어'),
    ('기술', 'IT 서비스', 'IT·소프트웨어', 'IT 서비스'),
    ('기술', '응용 소프트웨어', 'IT·소프트웨어', '응용 소프트웨어'),
    ('기술', '인프라 소프트웨어', 'IT·소프트웨어', '인프라 소프트웨어'),
    ('금융', '지역 은행', '은행', '지역 은행'),
    ('금융', '종합 은행', '은행', '종합 은행'),
    ('금융', '생명 보험', '보험', '생명 보험'),
    ('금융', '손해 보험', '보험', '손해 보험'),
    ('금융', '자본 시장', '증권', '자본 시장'),
    ('금융', '금융 정보·증권거래소', '증권', '금융 정보·증권거래소'),
    ('금융', '신용 서비스', '기타 금융', '신용 서비스'),
    ('금융', '복합 금융', '기타 금융', '복합 금융'),
    ('기초 소재', '화학', '화학', '화학'),
    ('기초 소재', '특수 화학', '화학', '특수 화학'),
    ('기초 소재', '철강', '금속', '철강'),
    ('기초 소재', '기타 산업 금속·광업', '금속', '기타 산업 금속·광업'),
    ('기초 소재', '종이·종이 제품', '종이·건자재', '종이·종이 제품'),
    ('기초 소재', '건축 자재', '종이·건자재', '건축 자재'),
    ('필수 소비재', '가공 식품', '식품', NULL),
    ('필수 소비재', '주류 제조', '주류·담배', '주류 제조'),
    ('필수 소비재', '담배', '주류·담배', '담배'),
    ('필수 소비재', '생활·개인용품', '생활용품', NULL),
    ('필수 소비재', '식료품 소매', '소매', '식료품 소매'),
    ('필수 소비재', '할인점', '소매', '할인점'),
    ('헬스케어', '전문·복제약 제조', '제약', '전문·복제약 제조'),
    ('헬스케어', '종합 제약', '제약', '종합 제약'),
    ('헬스케어', '의료 기기', '의료 기기·서비스', '의료 기기'),
    ('헬스케어', '의료 장비·용품', '의료 기기·서비스', '의료 장비·용품'),
    ('헬스케어', '의료 정보 서비스', '의료 기기·서비스', '의료 정보 서비스'),
    ('커뮤니케이션', '통신 서비스', '통신', NULL),
    ('커뮤니케이션', '게임·멀티미디어', '게임', NULL),
    ('커뮤니케이션', '광고 대행', '미디어·광고', '광고 대행'),
    ('커뮤니케이션', '인터넷 콘텐츠·정보', '미디어·광고', '인터넷 콘텐츠·정보'),
    ('커뮤니케이션', '엔터테인먼트', '미디어·광고', '엔터테인먼트'),
    ('부동산', '복합 부동산', '부동산', '복합 부동산'),
    ('부동산', '부동산 서비스', '부동산', '부동산 서비스'),
    ('부동산', '부동산 개발', '부동산', '부동산 개발'),
    ('유틸리티', '규제 전력', '전력', '규제 전력'),
    ('유틸리티', '복합 유틸리티', '전력', '복합 유틸리티'),
    ('유틸리티', '재생 에너지 유틸리티', '전력', '재생 에너지 유틸리티'),
    ('유틸리티', '규제 가스', '가스', NULL),
    ('에너지', '석유·가스 정제·판매', '석유·가스', '석유·가스 정제·판매'),
    ('에너지', '석유·가스 탐사·생산', '석유·가스', '석유·가스 탐사·생산')
) AS v(sector, old_name, middle, sub)
WHERE s.market = 'JAPAN' AND s.sector_name = v.sector AND s.industry_name = v.old_name;

-- 이름만 바뀌는 업종
UPDATE overseas_classification_category c SET name = '상사'
FROM overseas_classification_category p
WHERE c.parent_id = p.id AND p.parent_id IS NULL AND p.market = 'JAPAN' AND p.name = '산업재' AND c.name = '복합 기업';
UPDATE overseas_classification_category c SET name = '전기 장비'
FROM overseas_classification_category p
WHERE c.parent_id = p.id AND p.parent_id IS NULL AND p.market = 'JAPAN' AND p.name = '산업재' AND c.name = '전기 장비·부품';
UPDATE overseas_classification_category c SET name = '식품'
FROM overseas_classification_category p
WHERE c.parent_id = p.id AND p.parent_id IS NULL AND p.market = 'JAPAN' AND p.name = '필수 소비재' AND c.name = '가공 식품';
UPDATE overseas_classification_category c SET name = '생활용품'
FROM overseas_classification_category p
WHERE c.parent_id = p.id AND p.parent_id IS NULL AND p.market = 'JAPAN' AND p.name = '필수 소비재' AND c.name = '생활·개인용품';
UPDATE overseas_classification_category c SET name = '통신'
FROM overseas_classification_category p
WHERE c.parent_id = p.id AND p.parent_id IS NULL AND p.market = 'JAPAN' AND p.name = '커뮤니케이션' AND c.name = '통신 서비스';
UPDATE overseas_classification_category c SET name = '게임'
FROM overseas_classification_category p
WHERE c.parent_id = p.id AND p.parent_id IS NULL AND p.market = 'JAPAN' AND p.name = '커뮤니케이션' AND c.name = '게임·멀티미디어';
UPDATE overseas_classification_category c SET name = '가스'
FROM overseas_classification_category p
WHERE c.parent_id = p.id AND p.parent_id IS NULL AND p.market = 'JAPAN' AND p.name = '유틸리티' AND c.name = '규제 가스';

-- 새 중분류
INSERT INTO overseas_classification_category (market, parent_id, name)
SELECT 'JAPAN', p.id, v.name
FROM (VALUES
    ('산업재', '운송·물류'),
    ('산업재', '기계'),
    ('산업재', '건설'),
    ('산업재', '기타 서비스'),
    ('경기 소비재', '자동차'),
    ('경기 소비재', '소매'),
    ('경기 소비재', '기타 소비재'),
    ('기술', '하드웨어'),
    ('기술', 'IT·소프트웨어'),
    ('금융', '은행'),
    ('금융', '보험'),
    ('금융', '증권'),
    ('금융', '기타 금융'),
    ('기초 소재', '금속'),
    ('기초 소재', '종이·건자재'),
    ('필수 소비재', '주류·담배'),
    ('필수 소비재', '소매'),
    ('헬스케어', '제약'),
    ('헬스케어', '의료 기기·서비스'),
    ('커뮤니케이션', '미디어·광고'),
    ('부동산', '부동산'),
    ('유틸리티', '전력'),
    ('에너지', '석유·가스')
) AS v(sector, name)
JOIN overseas_classification_category p ON p.market = 'JAPAN' AND p.parent_id IS NULL AND p.name = v.sector
ON CONFLICT (market, parent_id, name) WHERE parent_id IS NOT NULL DO NOTHING;

-- 옛 업종을 새 중분류 아래 소분류로 옮긴다.
UPDATE overseas_classification_category c
SET parent_id = mid.id
FROM (VALUES
    ('산업재', '철도', '운송·물류'),
    ('산업재', '해상 운송', '운송·물류'),
    ('산업재', '항공', '운송·물류'),
    ('산업재', '트럭 운송', '운송·물류'),
    ('산업재', '종합 화물·물류', '운송·물류'),
    ('산업재', '특수 산업 기계', '기계'),
    ('산업재', '농업·건설 중장비', '기계'),
    ('산업재', '공구·부속품', '기계'),
    ('산업재', '엔지니어링·건설', '건설'),
    ('산업재', '건축 제품·장비', '건설'),
    ('산업재', '컨설팅 서비스', '기타 서비스'),
    ('산업재', '사무 장비·용품', '기타 서비스'),
    ('산업재', '보안·경비 서비스', '기타 서비스'),
    ('경기 소비재', '자동차 제조', '자동차'),
    ('경기 소비재', '자동차 부품', '자동차'),
    ('경기 소비재', '백화점', '소매'),
    ('경기 소비재', '인터넷 소매', '소매'),
    ('경기 소비재', '의류 소매', '소매'),
    ('경기 소비재', '가구·비품·가전', '소매'),
    ('경기 소비재', '주택 건설', '기타 소비재'),
    ('경기 소비재', '섬유 제조', '기타 소비재'),
    ('기술', '반도체 장비·소재', '반도체'),
    ('기술', '과학·기술 기기', '전자 부품'),
    ('기술', '소비자 전자제품', '하드웨어'),
    ('기술', '컴퓨터 하드웨어', '하드웨어'),
    ('기술', 'IT 서비스', 'IT·소프트웨어'),
    ('기술', '응용 소프트웨어', 'IT·소프트웨어'),
    ('기술', '인프라 소프트웨어', 'IT·소프트웨어'),
    ('금융', '지역 은행', '은행'),
    ('금융', '종합 은행', '은행'),
    ('금융', '생명 보험', '보험'),
    ('금융', '손해 보험', '보험'),
    ('금융', '자본 시장', '증권'),
    ('금융', '금융 정보·증권거래소', '증권'),
    ('금융', '신용 서비스', '기타 금융'),
    ('금융', '복합 금융', '기타 금융'),
    ('기초 소재', '특수 화학', '화학'),
    ('기초 소재', '철강', '금속'),
    ('기초 소재', '기타 산업 금속·광업', '금속'),
    ('기초 소재', '종이·종이 제품', '종이·건자재'),
    ('기초 소재', '건축 자재', '종이·건자재'),
    ('필수 소비재', '주류 제조', '주류·담배'),
    ('필수 소비재', '담배', '주류·담배'),
    ('필수 소비재', '식료품 소매', '소매'),
    ('필수 소비재', '할인점', '소매'),
    ('헬스케어', '전문·복제약 제조', '제약'),
    ('헬스케어', '종합 제약', '제약'),
    ('헬스케어', '의료 기기', '의료 기기·서비스'),
    ('헬스케어', '의료 장비·용품', '의료 기기·서비스'),
    ('헬스케어', '의료 정보 서비스', '의료 기기·서비스'),
    ('커뮤니케이션', '광고 대행', '미디어·광고'),
    ('커뮤니케이션', '인터넷 콘텐츠·정보', '미디어·광고'),
    ('커뮤니케이션', '엔터테인먼트', '미디어·광고'),
    ('부동산', '복합 부동산', '부동산'),
    ('부동산', '부동산 서비스', '부동산'),
    ('부동산', '부동산 개발', '부동산'),
    ('유틸리티', '규제 전력', '전력'),
    ('유틸리티', '복합 유틸리티', '전력'),
    ('유틸리티', '재생 에너지 유틸리티', '전력'),
    ('에너지', '석유·가스 정제·판매', '석유·가스'),
    ('에너지', '석유·가스 탐사·생산', '석유·가스')
) AS v(sector, name, middle)
JOIN overseas_classification_category p ON p.market = 'JAPAN' AND p.parent_id IS NULL AND p.name = v.sector
JOIN overseas_classification_category mid ON mid.parent_id = p.id AND mid.name = v.middle
WHERE c.parent_id = p.id AND c.name = v.name;

-- 중분류와 이름이 같은 소분류, 새 소분류
INSERT INTO overseas_classification_category (market, parent_id, name)
SELECT 'JAPAN', mid.id, v.name
FROM (VALUES
    ('기술', '반도체', '반도체'),
    ('기술', '전자 부품', '전자 부품'),
    ('기초 소재', '화학', '화학'),
    ('산업재', '기타 서비스', '인쇄')
) AS v(sector, middle, name)
JOIN overseas_classification_category p ON p.market = 'JAPAN' AND p.parent_id IS NULL AND p.name = v.sector
JOIN overseas_classification_category mid ON mid.parent_id = p.id AND mid.name = v.middle
ON CONFLICT (market, parent_id, name) WHERE parent_id IS NOT NULL DO NOTHING;
