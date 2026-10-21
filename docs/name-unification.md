# Marketry 명칭 통일

## 범위와 완료 기준

`market-monitor`와 `market_monitor`를 각각 `marketry`로 통일한다. 접미사는 유지한다.
예: `market-monitor-renderer` → `marketry-renderer`, `market_monitor_db` → `marketry_db`.
외부 서비스 이름인 Kiwoom은 대상이 아니다.

완료는 검색 결과 0건이 아니라 현재 실행 경로가 새 이름을 사용하고 기존 데이터·백업·복구가
유지되는 상태다. 과거 기록의 파일명과 이전 이미지에서 새 이미지로 복사하는 이관 원본은
실제 대상을 가리켜야 하므로 이름을 소급해서 바꾸지 않는다.

## 확인한 현재 상태

- 맥미니 운영 Compose는 이미 `marketry` 프로젝트, `marketry-postgres`, `marketry_db`,
  `marketry` DB 사용자와 `marketry-*` 이미지를 사용한다. 이번 작업을 이유로 운영 DB를 다시
  만들거나 복원하지 않는다.
- 백엔드 Java 패키지, Gradle 프로젝트, 프론트 소스는 이미 Marketry 명칭을 사용한다.
- 렌더러 npm 패키지·잠금 파일·시작 로그는 `marketry-renderer`로 변경했다.
- 상위 `start-backend.command`의 실행 변수를 `MARKETRY_BASE_URL`로 변경했다.
- 개발 Compose 프로젝트, DB, 사용자는 `marketry-local`, `marketry_db`, `marketry`로 전환했다.
  기존 개발 DB 컨테이너와 볼륨은 보존했다.
- 오라클 비상 서버는 예전 컨테이너·DB·체크아웃·환경 파일 경로를 유지하고, Release 워크플로가
  해당 체크아웃 경로를 직접 참조한다. 맥미니가 새 이름을 쓴다는 사실만으로 오라클을 바꾸지 않는다.

## 적용 순서

### 1. 실행 자원과 무관한 표기

렌더러 패키지와 잠금 파일의 이름, 시작 로그를 함께 변경한다. 상위 로컬 실행 변수와
개발 안내의 저장소 경로와 운영 문서의 현재 자산 이미지명을 수정한다.
검증은 JSON 파싱·잠금 파일 일치, Node·셸 문법 검사다.
운영 배포와 서버 재시작은 별도 요청 시 GitHub Actions로만 한다.

### 2. 개발 DB 전환 — 2026-10-21 완료

개발 OrbStack DB를 덤프하고 아카이브 목록을 확인했다. `rename-db.sh`로 새 볼륨의
`marketry_db`에 복원하고 임시 포트 15433에서 회원 수 4명이 일치하는지 검사한 뒤
기본 로컬 포트 15432로 전환했다. 기존 컨테이너와 볼륨은 보존했다.

`infra/local/compose.yml`, `restore-snapshot.sh`, 복원 회귀 검사, 로컬 프로필 설정,
수동 테스트의 개발 DB 연결 기본값, 상위 실행 스크립트와 개발 안내를 함께 바꿨다.
새 DB로 로컬 백엔드가 시작되고 Flyway 검증을 통과했다. 로컬 웹 로그인은 별도로 확인한다.

복원 스크립트는 이름 변경 이후의 개발 DB를 대상으로 고정했다. 스냅샷 복원 회귀 검사
6개가 통과했다. 기존 개발 DB 볼륨 삭제는 이 작업에 포함하지 않는다.

### 3. 오라클 비상 경로 — 개발자 선행 작업 필요

`.github/workflows/`는 이 세션에서 수정할 수 없다. Release가 예전 체크아웃 경로를
사용하므로 스크립트의 경로만 먼저 바꾸면 비상 배포가 실패한다.

워크플로를 관리할 수 있는 개발자가 체크아웃·환경 경로·Compose 서비스·nginx upstream·
DB 사용자와 DB명·백업 및 복구 경로의 전환을 함께 준비해야 한다. 기존 경로를 사용하는
일반 배포와 장기 비상 복구 준비를 구분하고, 중지된 서버를 명칭 변경 목적으로 기동하지 않는다.
현재 에이전트는 운영 SSH·Docker·자격 증명에 접근하거나 워크플로 제한을 우회하지 않는다.

`application-prod.properties`의 기본 DB·렌더러 주소도 이 단계에서 정리한다.
현재 맥미니 Compose는 새 이름의 URL·사용자·렌더러 주소를 명시적으로 주입한다.

## 남겨야 하는 이전 이름

- `infra/scripts/migrate-image-names.sh`와 대응 회귀 검사의 이전 GHCR 원본.
- `docs/decisions.md`, `docs/backlog.md`, 프론트 `docs/history.md` 등 당시 사실을 담은 기록.
- 실제 과거 덤프 파일명과 운영 전환 전 오라클 연결 이름.

오라클 비상 경로의 전환과 운영 배포는 완료되지 않았다.
