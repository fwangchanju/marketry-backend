-- 쓰는 코드가 없어진 테이블 8개를 지운다. 투자자·프로그램매매·공매도·지수기여도 랭킹 수집과
-- 관심종목 기능을 걷어내면서(marketry-backend#197·#199·#200) 이 테이블들에는 쓰기도 읽기도 없다.
-- 데이터를 지우는 마이그레이션이라 이 앱이 이 테이블을 매핑하는 엔티티가 남아 있는 jar가 떠 있을 때는
-- 적용하면 안 된다(ddl-auto=validate가 시작을 거부한다). 위 PR들을 배포한 뒤에만 병합한다.
DROP TABLE IF EXISTS program_trading_ranking_snapshot;
DROP TABLE IF EXISTS index_contribution_ranking_snapshot;
DROP TABLE IF EXISTS intraday_investor_ranking_snapshot;
DROP TABLE IF EXISTS investor_trading_summary_snapshot;
DROP TABLE IF EXISTS program_trading_daily;
DROP TABLE IF EXISTS program_trading_history;
DROP TABLE IF EXISTS short_selling_daily;
DROP TABLE IF EXISTS watch_stock;
