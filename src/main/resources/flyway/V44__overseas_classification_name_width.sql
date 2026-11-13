-- 미국 분류 이름은 야후 영문 이름(예: Communication Services 22자, Software - Infrastructure 25자)이라 12자를 넘는다.
-- 길이만 늘린다. 읽는 코드는 그대로 동작하고(옛 앱도 VARCHAR를 읽는다) 이 마이그레이션을 먼저 배포한 뒤 엔티티 길이를 바꾼다.
ALTER TABLE overseas_classification_category ALTER COLUMN name TYPE VARCHAR(40);
ALTER TABLE overseas_stock_classification ALTER COLUMN sector_name TYPE VARCHAR(40);
ALTER TABLE overseas_stock_classification ALTER COLUMN industry_name TYPE VARCHAR(40);
ALTER TABLE overseas_stock_classification ALTER COLUMN sub_industry_name TYPE VARCHAR(40);
