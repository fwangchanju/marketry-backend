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
- 렌더러 npm 패키지·잠금 파일·시작 로그에는 예전 이름이 남아 있다.
- 상위 `start-backend.command`의 `MARKET_MONITOR_BASE_URL`은 현재
  `marketry.base-url`에 대응하지 않는다. `MARKETRY_BASE_URL`로 고친다.
- 개발 Compose 프로젝트는 `market-monitor-local`, DB는 `market_monitor_db`, 사용자는
  `market_monitor`다. 연결 URL, 헬스체크, 스냅샷 복원 및 회귀 검사가 이 이름을 함께 쓴다.
- 오라클 비상 서버는 예전 컨테이너·DB·체크아웃·환경 파일 경로를 유지하고, Release 워크플로가
  해당 체크아웃 경로를 직접 참조한다. 맥미니가 새 이름을 쓴다는 사실만으로 오라클을 바꾸지 않는다.

## 적용 순서

### 1. 실행 자원과 무관한 표기

렌더러 패키지와 잠금 파일의 이름, 시작 로그를 함께 변경한다. 상위 로컬 실행 변수와
개발 안내의 저장소 경로와 운영 문서의 현재 자산 이미지명을 수정한다.
검증은 JSON 파싱·잠금 파일 일치, Node·셸 문법 검사다.
운영 배포와 서버 재시작은 별도 요청 시 GitHub Actions로만 한다.

### 2. 개발 DB 전환 — 아직 실행하지 않음

설정만 먼저 변경하면 기존 DB 대신 빈 볼륨을 만들거나 접속이 끊길 수 있으므로 이 단계는
데이터 전환과 한 묶음으로 준비한다. DB 변경 실행은 별도 명시적 승인을 받는다.

1. 로컬 백엔드와 예약 스냅샷 복원을 잠시 멈추고 진행 중인 복원이 없는지 확인한다.
   개발 OrbStack 소켓과 프로젝트를 확인하고 기존 개발 DB를 덤프한다. 덤프 목록 검사와
   임시 개발 DB 복원으로 백업을 검증한다. 운영 데몬과 환경 파일은 사용하지 않는다.
2. 기존 사용자 권한·볼륨 실명을 확인한다. 새 DB는 기존 DB와 포트가 겹치지 않도록
   사용하지 않는 임시 로컬 포트에 바인딩한다.
3. 새 `marketry-local` 프로젝트 및 새 볼륨에 `marketry` 사용자와 `marketry_db`를 준비하고
   `--no-owner --no-privileges`로 덤프를 복원한다. 기존 컨테이너·볼륨은 보존한다.
4. `infra/local/compose.yml`, `restore-snapshot.sh`, 복원 회귀 검사,
   `application.properties`, `application-local.properties`, 수동 테스트의 연결 기본값,
   상위 실행 스크립트·개발 안내·`infra/prod/README.md`의 개발 복원 설명을 함께 바꾼다.
   `.env` 내용은 읽거나 수정하지 않는다.
5. 임시 포트에서 기존 DB와 테이블별 행 수·회원 설정을 비교하고 Flyway 검증 및 로컬 로그인을
   확인한다. 통과한 뒤 기존 DB 컨테이너만 중지하고 새 DB를 정상 로컬 포트로 다시 생성한다.
   스냅샷 복원의 대상도 새 프로젝트로 고정됐는지 모조 Docker 회귀 검사로 확인한다.
6. 실패하면 새 프로젝트를 중지하고 이전 설정으로 돌아가 기존 DB 컨테이너를 재기동한다. 기존 볼륨 삭제는
   이 작업에 포함하지 않는다. 성공 뒤 예약 복원을 재개한다.

이 단계 전에는 기존 개발 DB 이름과 그 이름을 검사하는 테스트를 유지한다.

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

이 문서는 전환 계획이며 개발 DB 전환·오라클 복구 검증 또는 운영 배포 성공을 증명하지 않는다.
