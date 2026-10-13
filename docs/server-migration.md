# 서버 이관 계획과 체크리스트

> 임시 문서다. 이관이 끝나고 안정화되면 `operations.md`를 새 구성으로 고쳐 쓰고 이 파일은 삭제한다.
> 결정 사항 중 오래 남길 것은 그때 `decisions.md`로 회수한다.

운영을 오라클 클라우드 서버 두 대에서 집에 있는 맥미니(M4, RAM 24GB, SSD 512GB) 한 대로 옮긴다.
맥미니는 운영 서버이면서 개발 환경이기도 하다. 비개발자가 러스트데스크로 맥미니에 붙어 에이전트에게
코드를 짜게 하고 로컬에서 화면을 확인한 뒤 배포까지 에이전트가 한다. 그래서 이 계획의 대부분은
"개발 쪽 에이전트가 실수로도 운영을 건드리지 못하게 하는 것"과 "그래도 사고가 나면 되돌릴 수 있게
하는 것"에 쓰인다.

## 진행 현황과 다음 작업 (2026-10-06 기준)

이어서 작업하는 세션은 이 절과 아래 체크리스트부터 본다. 운영 서버·운영 Docker·맥미니 `deploy` 영역은 에이전트가 직접
만지지 않는다. 사용자가 실행할 명령을 안내하고, 레포 변경은 PR로 한다.

맥미니 기반 설치, 운영 환경 파일 준비, 키움 프록시·R2 연결 사전 검증과 Google 브랜딩은 완료했다.
남은 큰 작업은 배포 경로 마무리, 실제 서버 이관, 운영·백업 검증, 안정화 뒤 정리다.
실제 운영 전환과 안정화 뒤 정리는 아직 시작하지 않았다. 체크 항목은 절차별 확인도 포함하므로 개수를 작업량이나 소요 시간으로 보지 않는다.

- 1단계(도메인·터널), 2단계(이름 변경)는 끝났다. Google 브랜딩은 도메인 소유권 확인과 인증·게시를 완료했다.
  사용자가 브랜딩이 인증되어 사용자에게 표시되는 상태를 확인했다. 가비아 자동 연장 대신 사용자가 매년 갱신 알림을 설정했다
- 3단계 게이트의 로컬 이미지 정리와 전체 배포 태그 처리를 로컬에서 보완했다. 배포 워크플로 변경과 실제 배포 검증은 남았다.
  맥미니 구성 파일과 게이트·백업 스크립트는 `infra/prod/`에 있다. 보완한 게이트는 PR로 검토하며 병합·재설치는 남았다
- 4단계 맥미니 기반은 계정 분리, Colima 부팅 기동, SSH, Tailscale 데몬, 에이전트 제한, GitHub 토큰까지 됐다.
  Tailscale 태그·ACL과 서버 키 만료 끄기도 확인했다. 운영 네트워크 생성, 게이트·백업 스크립트 설치와 배포 공개키 등록을 끝냈다.
  `deploy`의 GHCR 로그인과 앱 이미지 정보 조회를 확인했다. 사용자가 운영 환경 파일 값을 작성하고 `docker compose config --quiet` 검사를 통과했다.
  실제 연결 검증은 키움 프록시와 R2를 완료했으며 앱 전체의 외부 연동은 이관 당일 확인한다.
  운영 설정과 백업은 프로젝트별로 모아 `~/Projects/marketry/env/marketry.env`, `~/Projects/marketry/backups/{predeploy,daily}`를 사용한다.
  사용자가 폴더를 이동하고 설치된 게이트·백업 스크립트의 경로 수정과 구문 검사를 완료했다. 소스 원본의 경로 일치와 모조 검사도 통과했다.
  설치본은 경로만 수정했으며 미병합 게이트의 이미지 정리·태그 처리 기능은 아직 반영하지 않았다.
  게이트와 첫 수동 Compose 명령은 `MARKETRY_ENV_FILE`을 명시해 서비스별 환경 파일 경로를 맞춘다. 도구용 `~/Optional`과 macOS 기본 폴더는 유지한다.
  공유 스냅샷 폴더와 로그 폴더를 만들고 백업 LaunchDaemon 파일을 설치했다. plist 검사는 통과했으며 예약 등록은 이관 당일 DB 기동 뒤 진행한다.
  개발용 스냅샷은 공유가 필요해 `/Users/Shared/marketry-dev-snapshot`에 둔다. R2를 활성화하고 버킷과 업로드용 액세스 키를 만들었다.
  압축 DB 덤프 크기를 확인했고 무료 사용량의 여유를 고려해 `daily/` 접두사에 10일 뒤 삭제하는 규칙을 적용했다.
  기본 미완료 업로드 7일 뒤 정리 규칙은 유지한다. 맥미니 일일 백업 7일과 배포 전 백업 최근 5개도 유지한다.
  rclone 설치와 배포본 파일 검사를 완료했다. 개발 계정의 05:00 스냅샷 복원 LaunchAgent 파일도 준비하고 plist·구문 검사를 통과했다.
  개발 예약 작업은 첫 스냅샷 확인 뒤 활성화한다. OrbStack·JDK 21·Node 설치를 확인했고 로컬 실행 스크립트의 옛 레포 경로를 수정했다.
  개발 DB·백엔드·프론트 기동과 백엔드 헬스 응답을 확인했다. 사용자가 로컬 화면도 확인했다.
  사용자가 운영 환경 파일의 R2 설정으로 작은 임시 파일의 업로드·다운로드·내용 비교·삭제를 모두 통과했다.
  실제 DB 백업·스냅샷 검증과 예약 등록은 이관 당일 진행한다. 외부 감시는 사용자 요청으로 나중에 등록한다
- 5단계(키움 프록시)는 서버 2의 임시 공인 IP를 예약 IP로 교체했다. 외부에서 보이는 발신 IP가 예약 IP와 일치하고 tailnet SSH 접속도 되는 것을 확인했다.
  키움 허용 IP에 서버 2의 예약 IP를 추가했다. 추가 예약 IP 생성은 한도 오류로 실패했다.
  서버 2는 Ubuntu 22.04이며 Tailscale 설치·인증과 tinyproxy 설치를 끝냈다. tinyproxy는 부팅 시 자동 시작으로 등록됐다.
  기존 렌더러 연결은 VCN 내부 주소를 사용한다. GitHub의 `RENDERER_SERVER_HOST`는 예약 IP로 변경했다.
  tinyproxy는 tailnet IPv4에서만 수신하며 키움 API의 443 포트 연결은 통과하고 다른 도메인·HTTP·다른 포트는 차단됨을 서버 2에서 확인했다.
  키움 루트 주소의 응답은 HTTP 500이었다. 이후 전용 Java 검사로 개발 계정의 OrbStack과 `deploy`의 Colima에서 실제 인증·조회를 검증했다.
  양쪽 모두 프록시를 통한 토큰 발급과 `ka20001` 코스피 지수 조회가 성공 코드 `0`과 양수 지수값으로 통과했다.
  tinyproxy에 Tailscale 선행 기동과 장애 시 5초 간격 재시작을 적용했다. ACL 정책을 저장하고 서버 2에 `tag:proxy`를 지정했다.
  소유자 기기에서 맥미니와 서버 2의 SSH 접속을 확인했다. 서버 2는 기존 SSH 로그인 계정으로 접속한 뒤 작업 계정으로 전환한다.
  맥미니의 `tag:macmini`와 두 서버의 키 만료 끄기를 확인했다. 맥미니에서도 키움 HTTPS 연결과 다른 도메인·HTTP·다른 포트 차단을 확인했다.
  서버 1에서 현재 렌더러의 헬스체크가 HTTP 200으로 응답했다. 프록시 통로 준비와 맥미니 컨테이너에서의 인증·조회 사전 검증을 완료했다
- 프록시 환경변수 두 개를 `MARKETRY_PROXY_HOST`, `MARKETRY_PROXY_PORT`로 바꿨다. 코드·템플릿·문서를 PR로 반영하며 병합·배포는 남았다.
  맥미니 운영 환경을 준비할 때 새 이름을 사용한다. 키움 앱키·인증 비밀키 변수 이름은 유지한다
- Actions용 Tailscale OAuth 클라이언트(`tag:ci`)를 만들고 `TAILSCALE_OAUTH_CLIENT_ID`, `TAILSCALE_OAUTH_CLIENT_SECRET`을 등록했다.
  맥미니 배포용 `MACMINI_SERVER_HOST`, `MACMINI_SERVER_USER`, `MACMINI_SERVER_SSH_KEY`도 등록했다.
  SSH 키는 `deploy` 홈의 기존 레포 clone용 키를 재사용한다. 공개키 지문이 GitHub Deploy key와 일치함을 확인했다.
  `deploy` 레포를 최신 `main`으로 갱신하고 게이트·백업 스크립트를 레포 밖에 설치했다.
  `authorized_keys`에 배포 게이트 강제 명령과 `restrict`로 공개키를 등록했다.
  게이트 입력 검사를 통과했고 루프백 SSH의 일반 명령이 게이트에서 거부됨을 확인했다. Actions에서의 실제 접속은 아직 검증하지 않았다
- 배포 흐름을 대조했다. 기존 GHCR 정리는 Actions에서 태그 20개 유지와 `main`·`deployed`·`previous` 보호로 설정돼 있다.
  오라클 앱 배포는 서버 디스크의 실행 중 이미지와 최신 이미지 2개를 보호하고 나머지를 정리한다.
  기존 맥미니 설치본에는 로컬 이미지 정리가 없고 `all`이 세 이미지에 같은 태그를 적용한다.
  로컬 원본에 이미지 정리와 앱 요청 태그·nginx 및 렌더러 `latest` 분리를 구현했다. 실제 Docker를 사용하지 않는 Bash 회귀 검사 13개를 통과했다.
  보완본을 병합·재설치하고 헬스 실패 시 워크플로의 원복이 유지되는지 확인한 뒤 실제 배포를 검증한다.
  기존 `CR_PAT`는 오라클 이미지 다운로드와 Actions의 GHCR 정리에 쓰므로 유지한다.
  맥미니 `deploy`의 GHCR 로그인은 Keychain 저장 오류를 해결했다. GHCR만 파일에 인증 정보를 저장하도록 설정하고 파일 권한을 `600`으로 두었다.
  사용자가 기존 `CR_PAT`로 로그인하고 앱의 `deployed` 이미지 정보 조회를 성공했다. 개발 계정의 로그인 성공과 구분해 확인했다

다음 순서:

1. 배포 경로를 마무리한다. 운영 환경 파일의 Compose 검사와 R2 연결 검증은 완료했다. 사용자가 커밋 보류를 해제하고 PR 작성을 요청했다.
   PR 검증·병합 뒤 사용자가 게이트 보완본을 재설치한다.
   배포 워크플로 변경은 개발자 확인이 필요하다.
   Actions가 Tailscale(`tag:ci`, 임시 노드)로 붙어 `ssh deploy@macmini "deploy <target> <tag>"`만 보낸다.
   `:deployed`/`:previous` 포인터 갱신과 원복은 지금 `release.yml`처럼 워크플로가 맡는다. 이관 기간에는 오라클 배포 경로도 남긴다.
   Tailscale OAuth와 맥미니 접속 시크릿, 게이트 설치와 배포 공개키 등록은 끝났다. Actions에서의 실제 접속을 검증한다
2. 이관 전 설치와 배포 경로 검증을 마친다. 키움 프록시의 OrbStack·Colima 인증·조회 사전 테스트는 통과했다.
   운영 앱과 수집·텔레그램 예약 작업은 이관 당일에 기동한다. 실제 캡처는 맥미니 앱·nginx·렌더러 기동 뒤 공개 터널 전환 전에 확인한다
3. 이관 당일(6단계)

Google 브랜딩의 도메인 소유권 확인과 인증·게시를 완료했다. 도메인 갱신은 사용자가 설정한 연례 알림에 따라 수동으로 한다.
`main` 대상의 별도 ruleset에 `Migration guard` 필수 체크를 등록하고 bypass를 비웠다.
기존 `protect-main`의 리뷰 필수와 `Maintain` 리뷰 우회 예외는 유지했다. 리뷰 우회와 마이그레이션 검사 우회를 별도 ruleset으로 구분한다.

해당 단계에서 사용자에게 다시 알릴 것:

- 서버 2는 예약 IP로 교체했다. 현재 운영 렌더러는 아직 서버 2에 있다. `RENDERER_URL`은 VCN 내부 주소라 유지하고 사용자 SSH는 tailnet 주소를 쓴다.
  `RENDERER_SERVER_HOST`도 예약 IP로 변경했다. IP 값은 이 문서나 PR에 적지 않는다
- 배포 워크플로 PR: 맥미니에서 앱과 렌더러를 함께 실행한다. 서버 2는 이관 뒤 키움 프록시로만 쓴다.
  맥미니 배포용 시크릿은 `MACMINI_SERVER_HOST`, `MACMINI_SERVER_USER`, `MACMINI_SERVER_SSH_KEY`로 만들고 워크플로 참조를 함께 맞춘다.
  호스트는 tailnet 주소, 사용자는 `deploy`, 키는 기존 레포 clone용 키를 재사용한다. 맥미니 접속에는 배포 게이트 강제 명령을 적용한다.
  오라클 서버 1의 `SERVER_*`는 되돌리기 경로를 정리할 때까지 유지한다.
  수동 설치하는 프록시용 GitHub 시크릿은 지금 추가하지 않는다
- 안정화 뒤: 되돌리기용 오라클 배포 경로를 정리한 뒤 `RENDERER_SERVER_HOST`, `RENDERER_SERVER_USER`, `RENDERER_SERVER_SSH_KEY`를 정리한다.
  서버 1은 불필요한 서비스를 정리하고 재사용할 계획이므로 인스턴스 삭제를 전제로 안내하지 않는다
- 서버 2의 로그인 계정 이름은 이관 작업이 모두 끝난 뒤 변경한다. 지금은 기존 계정으로 설치·설정한다.
  새 이름이 시스템 계정과 겹치는지 확인하고 SSH 접속 설정도 함께 정리한다

---

## 게이트 보완 범위와 검증

전체 배포는 앱에 요청한 태그를 적용하고 nginx·렌더러는 `latest`로 배포한다. 두 서비스에 앱의 포인터 태그를 새로 만들지 않는다.
개별 nginx·렌더러 배포는 기존처럼 요청한 태그를 받는다. 기존 워크플로와 GitHub의 `CR_PAT`는 유지한다.
맥미니 GHCR 로그인은 사용자가 보관한 기존 `CR_PAT` 원문으로 한다. 토큰 값은 에이전트나 레포에 전달하지 않는다.

로컬 이미지 정리는 요청한 서비스에만 적용한다. 각 서비스의 실행 이미지와 직전 성공 배포 이미지, 생성 시각 기준 최신 고유 이미지 2개를 보호한다.
성공한 current/previous 상태를 실제 컨테이너 상태와 구분해 기록한다. 동일 성공 이미지를 재배포하거나 실패한 배포에서 복구하면 previous를 유지한다.
교체 전 실제 컨테이너 이미지도 해당 실행 동안 추가로 보호한다. 이전 실행의 상태 기록 실패가 원복 후보 삭제로 이어지지 않도록 한다.
오래된 태그 없는 이미지도 정리할 수 있도록 정확한 레포에서 확인한 전체 `sha256:` 이미지 ID를 `deploy` 홈에 기록한다.
배포 전 이미지 목록과 초기 실행 이미지를 기록하고 대상 전체의 헬스체크 성공 뒤 성공 상태를 원자적으로 저장한 다음 정리한다. 실패한 배포에서는 정리하지 않는다.
추적한 적 없는 태그 없는 이미지의 소유권은 추측하지 않는다. 다른 컨테이너는 중지 컨테이너까지 보호하고 다른 레포의 태그나 digest 참조가 붙은 이미지도 보호한다.
강제 삭제와 전역 prune은 사용하지 않는다. 메타데이터 조회나 상태 기록 실패 시 삭제를 건너뛴다. 정리 실패는 경고로 남기고 정상 배포의 성공 종료를 유지한다.
운영 DB·볼륨·네트워크는 정리 대상에 포함하지 않는다.

검증은 실제 Docker와 운영 환경 파일을 사용하지 않는 모조 CLI로 한다. 전체 배포의 태그 분리, 개별 배포 태그 유지,
배포 실패 시 삭제 없음, 오래된 실행·직전 이미지 보호, 최신 2개 보호, 태그 없는 오래된 이미지 정리,
다른 서비스·중지 컨테이너·레포·digest 보호, 동일 이미지 재배포, 실패 후 복구 시 previous 유지, 조회·기록·정리 실패 시 정상 배포 결과 유지를 확인한다.
모조 pull은 태그를 새 이미지로 옮기고 예전 이미지를 태그 없는 상태로 남긴다. 예상하지 않은 모조 명령은 실패시킨다.
macOS `/bin/bash` 3.2에서 구문과 회귀 검사를 실행한다. 게이트 호출 실패 자체가 다음 워크플로의 원복 조건에 포함돼야 한다.
컴파일·일반 테스트·포맷 검사도 실행한다. 변경한 설치본의 재설치와 실제 Actions 배포 검증은 별도 단계다.
사용자가 커밋 보류를 해제했다. 검증한 변경을 PR로 올리고 병합 뒤 운영 설치본에 반영한다.

---

## 외부 연동 검증 순서

키움 프록시는 이관 전에 전용 테스트 코드로 실제 동작을 검증한다. 캡처는 맥미니에 앱·nginx·렌더러를 배포한 뒤 확인한다.
키움 검사 실패 시 이관을 시작하지 않고 캡처 검사 실패 시 공개 터널을 전환하지 않는다.
키움 전용 테스트와 사용자 실행용 독립 JAR를 작성했다. 일반 회귀 검사 19개와 컴파일·포맷 검사, JAR의 발급 거부 동작을 확인했다.
2026-10-06 사용자가 개발 계정의 OrbStack에서 먼저 실행하고, 이어 `deploy`의 Colima 운영 네트워크에서 같은 JAR로 검사했다.
두 환경 모두 실제 키움 토큰 발급과 `ka20001` 조회가 성공 코드 `0`과 양수 지수값으로 통과했다.
에이전트는 운영 환경 파일·자격 증명을 읽거나 운영 Docker를 실행하지 않는다.

키움 검사는 앱과 같은 HTTP 클라이언트 설정을 사용해 프록시 인증과 읽기 전용 조회의 정상 응답을 확인한다.
프록시 설정이 비면 직접 연결로 대신하지 않고 검사를 실패 처리한다. 전체 앱 기동 없이 검사하거나 예약 작업을 명시적으로 비활성화한다.
기존 `KiwoomApiVerificationTest`는 prod 컨텍스트와 수집기를 사용하고 정상 응답 단언이 없으므로 그대로 재사용하지 않는다.
맥미니 호스트에서의 curl 성공에 더해 앱이 실행될 Colima 컨테이너에서도 프록시에 도달하는지 확인한다.
기존 운영 앱키로 토큰을 발급하기 전에는 기존 토큰에 미치는 영향을 확인한다. 현재 영향 여부는 미확인이다.
키·토큰·인증 헤더·응답 원문은 출력하지 않고 성공 여부와 비밀이 아닌 검증 결과만 남긴다.

전용 검사는 `KiwoomProxyManualTest`로 작성하고 `kiwoomProxyTest` 태스크로 그 클래스만 실행한다.
Spring 컨텍스트·DB·수집기·스케줄러를 기동하지 않고 `ApplicationConfig`의 키움 클라이언트와 실제 토큰 관리자를 직접 구성한다.
시장 스냅샷 수집기의 `SectorCurrentPriceRequest`로 코스피 종합 지수 조회를 한 번만 요청한다. 연속조회와 재시도는 하지 않는다.
이 API는 날짜를 받지 않으므로 지정일 조회로 표현하지 않는다. 성공 시 성공 코드와 비밀이 아닌 지수값만 출력한다.
JUnit 검사는 프로세스에 주입된 키움 인증 변수와 프록시 변수만 사용한다.
사용자 실행용 `KiwoomProxyCheck`는 `--env-file`로 명시한 파일에서 인증·프록시 네 변수만 선별한다. 파일을 셸로 실행하지 않는다.
에이전트는 이 프로그램으로 실제 환경 파일을 읽지 않는다. 사용자가 개발 계정 또는 deploy 계정에서 각 환경 파일을 지정해 실행한다.
프록시 누락·잘못된 포트·발급 허용 플래그 누락은 요청 전에 실패한다. 사용자는 기존 토큰 영향을 확인한 뒤 `MARKETRY_KIWOOM_TOKEN_TEST=1`로 발급을 허용한다.
토큰 존재와 지수 조회의 성공 코드·숫자 데이터를 단언한다. 지수는 기존 파서로 부호·콤마를 정규화하고 절댓값이 양수인지 확인한다.
검사 태스크는 이전 성공 결과나 빌드 캐시로 대체하지 않고 매번 실행한다. 실패 시 원래 예외나 응답 내용을 결과 파일에 남기지 않는다.
일반 테스트는 설정 거부와 실패 정보 노출 방지를 비밀값·외부 네트워크 없이 검증하고 실제 키움 검사는 일반 `test`에서 제외한다.
`kiwoomProxyCheckJar` 태스크는 `build/libs/kiwoom-proxy-check.jar`를 만든다. 검토된 실행 파일은 사용자가 deploy 소유로 복사한다.
실제 실행은 사용자가 OrbStack에서 먼저 확인하고 맥미니 Colima 컨테이너에서 수행한다. 에이전트는 비밀값 없이 일반 회귀 검사와 컴파일·포맷·패키징 검사를 한다.

캡처는 이관 당일 기존 앱 중지와 DB 복원을 마치고 맥미니 앱·nginx·렌더러를 기동한 뒤 검사한다.
렌더러는 내부 도메인 별칭으로 nginx에 접근하므로 공개 터널은 아직 오라클에 둔 상태에서 검사한다.
실제 앱의 캡처 인증과 화면 데이터 로딩을 거쳐 PNG가 생성되는지 확인하고 이미지 내용도 검토한다.
소유자 캡처 토큰과 실제 텔레그램 대상인 map·sector 화면을 사용한다.
기존 `ScreenshotClientManualTest`는 소유자 인증 없이 SUMMARY를 대상으로 하므로 그대로 실행해서 완료 처리하지 않는다.
임의 테스트 페이지 캡처나 렌더러 헬스체크만으로 완료 처리하지 않는다. 검사 전용 스택을 별도로 만드는 작업은 하지 않는다.
최종 DB 적재와 터널 전환 뒤의 확인은 사전 테스트에 더해 별도로 수행한다.

## 목표 구성

### 계정

| 계정 | 종류 | 쓰임 |
|---|---|---|
| 개발 계정 | 일반 | 사람과 에이전트가 모두 여기서 작업한다. 러스트데스크 세션도 이 계정이다 |
| `deploy` | 일반 | 운영 컨테이너, 운영 DB 볼륨, 환경변수, 백업을 소유한다. GitHub Actions가 SSH로 들어오는 계정 |
| `admin` | 관리자 | 비밀번호는 소유자만 안다. 로그인하지 않고 `su - admin`이나 인증 창에만 쓴다 |

개발 계정을 관리자로 두고 sudo 비밀번호로만 막는 안도 검토했다. 에이전트가 인증 창을 띄우면 비개발자가
비밀번호를 넣을 수 있어서 접었다. 경계는 사람의 행동이 아니라 OS 권한이어야 한다.

`deploy` 홈은 `700`이다. 개발 계정에서는 운영 파일을 읽을 수도 운영 Docker에 붙을 수도 없다.

### 컨테이너 런타임

운영은 `deploy` 계정의 Colima(vz, CPU 4, 메모리 6GB, 디스크 100GB)로 돌리고 부팅 시 LaunchDaemon으로
뜨게 한다. 아무도 로그인하지 않아도 운영이 올라와야 해서다. OrbStack은 GUI 로그인이 필요하고 여러 계정이
동시에 쓸 수 없어 운영에는 못 쓴다.

개발은 개발 계정의 OrbStack(메모리 상한 8GB)이다. 쓰지 않는 메모리를 macOS에 돌려줘서 개발용으로는
이쪽이 낫다.

운영 Colima 안에는 PostgreSQL, 애플리케이션, nginx, 렌더러, cloudflared가 같은 네트워크로 뜬다.
호스트에 여는 포트는 없다. 개발 쪽 포트(DB `15432`, 백엔드 `18081`, 프론트 `5173`)와 부딪힐 일이 없다.

#### 맥미니에 실제로 구성한 것 (2026-10-04)

`deploy`는 개발 계정 소유인 Homebrew(`/opt/homebrew`)를 쓰지 않는다. 개발 계정이 그 실행 파일을 바꿔치기하면
`deploy` 권한으로 실행되기 때문이다. 그래서 Colima, Lima, Docker CLI, Docker Compose를 `deploy` 홈에 직접 받았다.

```
/Users/deploy/                       (700)
├─ Optional/bin/                     colima, docker
├─ Optional/lima/                    Lima 배포본(bin, libexec, share)
├─ Optional/dl/                      내려받기 임시
├─ .docker/cli-plugins/              docker-compose
├─ Projects/marketry/
│  ├─ marketry-backend/              레포(읽기 전용 Deploy key로 clone)
│  ├─ env/                          비밀값(700, 파일 600)
│  └─ backups/                      운영 DB 백업(700)
└─ Library/Logs/colima.log           Colima 로그
```

- 내려받기는 `/tmp`가 아니라 `deploy` 홈 안에서 한다. `/tmp`는 다른 계정도 쓰는 곳이라 같은 이름 파일을 미리 만들어 둘 수 있다
- `~/.zshenv`에 `PATH`(`~/Optional/bin`, `~/Optional/lima/bin`, 시스템 경로만)와
  `DOCKER_HOST=unix:///Users/deploy/.colima/default/docker.sock`을 둔다. `.zprofile`은 SSH로 명령만 보낼 때 읽히지 않는다.
  `DOCKER_HOST`가 없으면 `docker`가 `/var/run/docker.sock`(개발 계정의 OrbStack)으로 붙으려 한다
- git은 `/usr/bin/git`을 쓴다
- Colima 프로필은 `default`(vz, CPU 4, 메모리 6GB, 디스크 100GB). 설정은 `~/.colima/default/colima.yaml`.
  CPU와 메모리는 다시 띄우면 바꿀 수 있고 디스크는 늘리기만 된다
- 부팅 기동: `/Library/LaunchDaemons/kr.co.marketry.colima.plist`(root:wheel, 644). `UserName deploy`,
  `colima start --foreground`, `RunAtLoad`, `KeepAlive`, `HOME`과 `PATH`를 직접 지정, 로그는 `~/Library/Logs/colima.log`.
  등록은 `sudo launchctl bootstrap system <plist>`
- 개발 계정 자동 로그인을 켠 채 재부팅해 LaunchDaemon이 Colima를 띄우는 것을 확인했다. 원격 접속(RustDesk)이 개발 계정
  로그인에 기대고 있어 자동 로그인은 끄지 않는다

#### 맥미니 관리 접속과 에이전트 제한 (2026-10-05)

- **SSH:** `/etc/ssh/sshd_config.d/010-marketry.conf`에 `PasswordAuthentication no`, `KbdInteractiveAuthentication no`,
  `PermitRootLogin no`. macOS 기본 파일(`100-macos.conf`)보다 먼저 읽히게 `010`으로 시작한다. 원격 로그인 허용 계정은
  `admin`, `deploy`. 원격 사용자 디스크 전체 접근은 끈다. `admin`은 소유자 PC의 키로만 들어온다
- **Tailscale:** 오픈소스 `tailscaled`만 로그인 전에 뜬다(App Store판과 Standalone판은 로그인 필요). `admin` 홈에 공식 Go를 받아
  `go install tailscale.com/cmd/tailscale{,d}@latest`로 빌드하고 `sudo tailscaled install-system-daemon`으로 설치했다.
  root로 도는 프로그램이라 개발 계정 소유 Homebrew의 Go나 tailscale을 쓰지 않는다. CLI도 `/usr/local/bin`에 root 소유로 둔다.
  기기 이름 `macmini`, 키 만료 끔. 맥미니 쪽 MagicDNS는 쓰지 않는다(프록시는 IP로 지정)
- **원격 화면:** RustDesk는 개발 계정 세션에서 쓴다. 설치 때 깔린 root 서비스(`com.carriez.RustDesk_service`)가 개발 계정 소유
  앱 파일을 root로 실행하는 구조라 `/Library/LaunchDaemons.disabled/`로 옮겨 껐다. 앱 파일 소유자 변경은 macOS 앱 관리 보호에
  막힌다. 로그인 화면에서의 원격 접속은 잃는데, 개발 계정 자동 로그인을 끄지 않는 한 문제없다. Tailscale IP로 직접 접속하려면
  RustDesk의 직접 IP 접속을 켠다
- **`admin`은 화면에 로그인하지 않는다.** `/Library/LaunchAgents`의 사용자 앱이 `admin` 권한으로 같이 뜨기 때문이다.
  터미널 작업은 SSH로, 화면 설정은 개발 계정 화면에서 관리자 인증 창으로 한다
- **에이전트 제한:** `/Library/Application Support/ClaudeCode/managed-settings.json`(root 소유)에 `sudo`, `su`, `login`, `dscl`,
  `dseditgroup`, `ssh`, `scp`, `sftp`, `osascript` 실행과 `deploy`·`admin` 홈 읽기를 deny로 두고 `disableBypassPermissionsMode`를 건다.
  Codex는 `/etc/codex/requirements.toml`에서 승인 정책을 `untrusted`, `on-request`로, 샌드박스를 `read-only`, `workspace-write`로 제한한다
- **에이전트 공통 지침(root 소유):** Claude Code는 관리형 `CLAUDE.md`(`/Library/Application Support/ClaudeCode/CLAUDE.md`), Codex는
  `/etc/codex/managed_config.toml`의 `developer_instructions`에 둔다. 내용은 "`.github/workflows/`는 고치지 않는다, 푸시가 workflow 권한으로
  거부되면 우회하지 말고 멈춘 뒤 사용자에게 개발자 확인이 필요하다고 알린다" 두 가지다. Claude Code 관리형 설정에는 워크플로 파일
  편집·쓰기, `gh auth` 로그인·전환·토큰 출력, `git remote set-url`/`add`도 deny로 더했다
- **GitHub 인증:** 개발 계정은 fine-grained 토큰(두 레포, Actions·Contents·Pull requests 쓰기, Commit statuses 읽기, 만료 없음)을
  `gh`에 넣고 `gh auth setup-git`으로 git도 쓰게 했다. 원격 주소는 HTTPS다. SSH 키는 권한 범위를 좁힐 수 없어서 맥미니 키를
  GitHub 계정에서 지웠다. 워크플로 파일 수정이 담긴 푸시는 GitHub가 거부한다

컨테이너별 메모리 제한은 렌더러에만 1GB로 건다. 6GB가 차면 리눅스가 아무 프로세스나 종료할 수 있는데,
메모리가 불어날 만한 건 Chromium을 띄우는 렌더러뿐이다. 제한이 있으면 넘쳤을 때 렌더러만 죽고 재시작한다.
지금 서버에서 평일 캡처를 거친 뒤 잰 최댓값(`memory.peak`)이 약 558MB였고 그 1.5~2배로 잡았다.
DB와 애플리케이션에는 걸지 않는다. 제한에 걸려 DB나 수집 중인 앱이 죽는 쪽이 더 나쁘다.

### 인바운드

새 도메인을 사서 Cloudflare DNS에 올리고 Cloudflare Tunnel로 받는다. 공유기 포트포워딩, DuckDNS IP 갱신,
Let's Encrypt는 쓰지 않는다. 맥미니가 바깥으로 연결을 여는 구조라 유동 IP 변경, 통신사 변경, 정전 후
재부팅, CGNAT의 영향을 받지 않는다. 집 IP도 드러나지 않는다.

포트포워딩과 DuckDNS를 쓰는 안도 검토했다. 집 IP가 공개되고, 공격을 받으면 집 인터넷 전체가 멈추고,
통신사 포트 차단과 NAT 루프백을 따로 확인해야 해서 접었다.

터널은 이관보다 먼저 지금 서버에 붙여 도메인 전환을 끝낸다. 그러면 이관 당일의 전환은 cloudflared를
어느 쪽에서 띄우느냐만 바꾸면 된다. 도메인 변경과 서버 변경이 한날에 겹치지 않는다.

렌더러는 nginx 컨테이너에 서비스 도메인을 네트워크 별칭으로 붙여 내부로 바로 접근한다. `CAPTURE_URL`은
`infra/renderer-docker-compose.yml`에 있으니 맥미니용 compose를 만들 때 내부 접근에 맞는지 같이 확인한다.

### 아웃바운드(키움)

키움 REST API는 허용 IP로만 호출된다. 맥미니는 유동 IP라서 키움 호출만 지금 렌더러가 도는 오라클 서버 2를
거친다. 서버 2에 tinyproxy를 두고 tailnet 주소에서만 받으며 `api.kiwoom.com:443`만 통과시킨다.

애플리케이션은 `kiwoomRestClient` 빈에만 프록시를 건다(`MARKETRY_PROXY_HOST`, `MARKETRY_PROXY_PORT`).
값이 비면 지금처럼 직접 연결하므로 이관 전에 배포해도 영향이 없다. 토큰 발급과 API 호출이 모두 이 빈을
쓴다. 키움 API 인증 환경변수 `KIWOOM_APP_KEY_B`, `KIWOOM_SECRET_B`는 이름을 유지한다.

서버 1이 아니라 서버 2를 프록시로 쓰는 건 이관 중 문제가 생기면 서버 1을 그대로 다시 올리기 위해서다.
오라클 유휴 회수는 수년간 겪지 않아 따로 대비하지 않는다. 회수되면 종량제로 바꾸거나 다른 곳의 최소
사양 서버에 tinyproxy와 Tailscale을 올리고 키움 허용 IP와 `MARKETRY_PROXY_HOST`만 바꾼다.

### Tailscale

| 기기 | 태그 | 허용 |
|---|---|---|
| 소유자 노트북 | 없음(소유자 계정) | 모든 기기 |
| 맥미니 | `tag:macmini` | `tag:proxy`의 8888만 |
| 오라클 서버 2 | `tag:proxy` | 없음 |
| GitHub Actions | `tag:ci` (임시 노드) | `tag:macmini`의 22만 |

Tailscale SSH는 켜지 않는다. 기기 인증만으로 SSH가 열리면 계정 분리가 무의미해진다. 서버 기기는
태그를 달아 키 만료를 끈다. 맥미니는 App Store 앱 대신 `tailscaled`를 부팅 데몬으로 설치한다.
로그인 없이 연결되고 개발 계정이 끌 수 없다.

### 배포

GitHub Actions가 배포할 때만 tailnet에 붙어 `deploy@맥미니`로 SSH 접속한다. 에이전트가 "배포해"
한마디로 병합과 배포를 하는 구조라 사람 승인 단계는 두지 않고 사고 뒤 되돌리는 장치를 둔다.

- `deploy`의 `authorized_keys`에 강제 명령을 걸어 SSH 접속이 항상 `deploy` 홈의 게이트 스크립트를
  거치게 한다. 게이트는 정해진 동작(배포, 헬스체크, 롤백)만 받고, 배포 동작이면 먼저 `pg_dump`를 뜬 뒤
  레포의 배포 스크립트를 실행한다. 덤프는 최근 5개만 남긴다. 게이트는 레포 밖에 있어서 에이전트가 배포
  절차를 바꿔도 직전 DB는 남는다
- 강제 명령을 쓰면 지금처럼 워크플로가 임의 스크립트를 SSH로 보낼 수 없다. 워크플로는 동작 이름과
  이미지 태그만 넘기도록 바꾼다
- 새 마이그레이션 SQL에 `DROP`, `TRUNCATE`, `DELETE`, 컬럼 삭제가 있으면 CI가 실패한다. 라벨 같은
  우회 수단은 두지 않고 브랜치 규칙의 bypass도 비운다. 파괴적 변경은 소유자가 `admin`이나 `deploy`로
  직접 DB에서 한다
- 맥미니의 GitHub 인증은 fine-grained PAT로 바꾼다. 두 레포에 Contents, Pull requests, Actions 쓰기만
  주고 Workflows, Administration, Secrets는 주지 않는다. 워크플로 파일 수정, 브랜치 규칙 변경, 시크릿
  접근이 막힌다

인프라 파일 변경 PR을 CI로 막는 안과 배포 알림도 검토했다. 인프라를 바꿀 때마다 소유자가 규칙을
껐다 켜야 해서 번거로움에 비해 얻는 게 적다고 보고 접었다.

에이전트가 계정 전환을 시도하지 않게 Claude Code 관리형 설정
(`/Library/Application Support/ClaudeCode/managed-settings.json`)에 `sudo`, `su`, `login`, `dscl`,
`ssh deploy@*` 등을 deny로 건다. Codex는 `/etc/codex/requirements.toml`로 샌드박스와 승인 정책을
강제한다. 둘 다 root 소유라 개발 계정이 고칠 수 없다. 실제 경계는 OS 권한이고 이건 한 겹 더 두는 장치다.

### 가용성과 백업

시스템 잠자기를 끄고(`pmset -a sleep 0`) 정전 후 자동 부팅을 켠다(`pmset -a autorestart 1`).
FileVault는 끈다. 켜져 있으면 재부팅 뒤 잠금 해제 화면에서 멈춰 운영, Tailscale, 터널이 모두 안 뜬다.
개발 계정으로 자동 로그인하고, macOS 업데이트는 자동 다운로드만 하고 주말에 손으로 설치한다.

맥미니나 집 인터넷이 죽으면 앱의 텔레그램 알림도 같이 죽는다. 그래서 외부 감시 서비스(UptimeRobot 등)가
5분마다 헬스체크를 부르게 한다.

`deploy`의 예약 작업이 매일 04:30에 `pg_dump`를 뜬다. 맥미니에 7일, Cloudflare R2에 10일을 남긴다.
R2 보관 기간은 압축 덤프 크기를 확인한 뒤 무료 사용량의 여유를 고려해 정했다. `daily/` 접두사에 10일 뒤 삭제하는 수명 주기 규칙을 적용한다.
배포 직전 덤프는 맥미니에 최근 5개를 남긴다. DB 크기가 늘면 저장량을 확인해 보관 기간을 재검토한다.

### 로컬 개발

DB만 OrbStack 컨테이너로 띄우고 백엔드와 프론트는 손으로 띄운다. 백엔드는 `local` 프로파일에
`auth.dev-login.enabled=true`, `scheduling.enabled=false`로 뜬다. 텔레그램과 키움 키는 개발 환경에 두지
않는다.

`deploy`가 매일 백업 직후 개발용 스냅샷을 만들어 `/Users/Shared/marketry-dev-snapshot/`에 둔다. 개발 계정은
읽기만 한다. 스냅샷은 `users` 이메일을 가리고 `user_refresh_token`은 `--exclude-table-data`로 행만 뺀다.
테이블까지 빼면 엔티티 검증과 dev-login이 깨진다. `users`를 통째로 빼는 안은 커스텀 테이블의 외래키가
깨져서 접었다.

개발 계정의 예약 작업이 05:00에 로컬 DB를 지우고 스냅샷으로 다시 만든다. 같은 일을 하는 수동 명령도 둔다.
브랜치에만 있는 마이그레이션은 다음 `bootRun` 때 Flyway가 적용한다.

### 이름

이관하면서 프로젝트 이름을 `marketry`로 통일한다. 서버 경로, 컨테이너, compose, DB, 이미지는 어차피 맥미니에서
새로 만들기 때문에 처음부터 새 이름으로 만들면 추가 비용이 거의 없다. 이관을 끝낸 뒤에 바꾸면 방금 만든 것을
운영 중에 다시 고쳐야 해서 더 번거롭다.

| 대상 | 지금 | 바꿀 이름 |
|---|---|---|
| 레포 | `market-monitor-backend`, `market-monitor-frontend` | `marketry-backend`, `marketry-frontend` |
| 이미지 | `market-monitor`, `-nginx`, `-renderer`, `-assets` | `marketry`, `marketry-nginx`, `marketry-renderer`, `marketry-assets` |
| 컨테이너 | `market-monitor-*` | `marketry-*` |
| DB | `market_monitor_db` | `marketry_db` |
| Docker 네트워크 | `proxy` | `marketry-network` (키움 프록시와 헷갈리지 않게) |
| Java 패키지 | `dev.eolmae.marketmonitor` | `dev.eolmae.marketry` |
| 설정 접두사 | `market-monitor.*` | `marketry.*` |

레포 이름을 바꾸면 GitHub가 옛 주소를 새 주소로 연결해 줘서 기존 clone과 서버의 `git fetch`는 깨지지 않는다.
프론트 배포 워크플로가 백엔드 레포 이름으로 nginx 재빌드를 부르는 곳(`repository: ...`)만 직접 고친다.

오라클 쪽은 옛 이름 그대로 둔다. 이관을 되돌릴 때 오라클에 남은 옛 이미지와 컨테이너를 그대로 다시 올린다.

---

## 체크리스트

### 1. 도메인과 터널 (지금 서버에서 먼저)

- [x] 도메인 구매. `.kr` 계열은 국내 업체에서 사고 네임서버를 Cloudflare로 바꾼다. DNSSEC는 바꾸기 전에 끈다
- [x] Cloudflare Tunnel 생성, 지금 서버에 cloudflared 기동
- [x] Google Auth Platform 리디렉션 URI를 새 도메인으로
- [x] Google Auth Platform 브랜딩(앱 이름, 홈페이지, 개인정보처리방침, 승인된 도메인)을 새 도메인으로. 소유권 확인과 인증·게시 완료
- [x] `market-monitor.base-url` 변경 후 배포
- [x] 옛 DuckDNS 주소에서 새 도메인으로 보내는 리디렉트
- [x] 새 도메인으로 로그인, 화면 확인. 맥미니 캡처는 6단계에서 앱·nginx·렌더러 기동 뒤 공개 터널 전환 전에 확인한다
- [x] 가비아 도메인 갱신 대응: 자동 연장 대신 매년 갱신 알림 설정

### 2. 이름 변경 (맥미니 설정 전에)

- [x] GitHub에서 두 레포 이름 변경
- [x] 프론트 배포 워크플로의 백엔드 레포 참조 수정
- [x] Java 패키지와 설정 접두사 변경. 동작이 바뀌지 않는 기계적 변경이라 별도 PR로 한다
- [x] 문서, `CLAUDE.md`, `AGENTS.md`, `spring.application.name`, 프론트 `package.json`, 이미지 라벨의 이름 참조
- [ ] 이미지, 컨테이너, DB, 서버 경로 이름은 3단계 코드 변경에서 맥미니용으로 새로 만들 때 반영한다

### 3. 코드 변경 (이관 전에 병합하고 지금 서버에 배포해 둔다)

- [x] 애플리케이션 이미지 amd64/arm64 멀티 빌드. 빌드 스테이지에 `--platform=$BUILDPLATFORM`
- [x] nginx 이미지, 렌더러 이미지 멀티 빌드. 프론트 assets는 정적 파일이라 nginx 빌드에서 빌드 머신 플랫폼으로 받으므로
      멀티 빌드가 필요 없다. 최종 스테이지에 `RUN`이 없어 QEMU도 쓰지 않는다
- [x] `kiwoomRestClient` 프록시 설정(`MARKETRY_PROXY_HOST`, `MARKETRY_PROXY_PORT`. 값이 비면 직접 연결)
- [ ] 프록시 환경변수 이름 변경을 PR로 병합하고 배포. 로컬 수정과 컴파일·일반 테스트·포맷 검사는 완료했다
- [x] 파괴적 마이그레이션 CI 검사(`ci.yml`의 `Migration guard` job). 로직은 PR에서 고칠 수 없게 워크플로 안에 둔다
- [x] `main` 대상의 `Migration guard` 전용 ruleset에 필수 체크를 등록하고 그 규칙의 bypass 비움(소유자가 웹에서).
      기존 `protect-main`의 리뷰 필수·`Maintain` 우회 예외는 유지하고 마이그레이션 검사만 별도 규칙으로 강제한다
- [x] 맥미니 `marketry-network` 서브넷 고정 생성 스크립트(`infra/prod/setup-network.sh`, `172.30.0.0/24`)
- [x] 맥미니용 compose(`infra/prod/compose.yml`, `nginx.conf`, `env.template`): cloudflared, 호스트 포트 없음, 렌더러 같은 네트워크·메모리 1GB,
      nginx 별칭 `marketry.co.kr`로 렌더러 내부 접근. 이미지 이름은 이관 뒤 정리 때 바꾼다
- [ ] 배포 워크플로: Tailscale 액션(`tag:ci`, 임시 노드), 대상 호스트를 맥미니로, SSH는 동작 이름과 태그만 전달
- [x] 맥미니 게이트의 로컬 이미지 정리 구현과 모조 회귀 검사. 실행 중 이미지와 원복용 이미지 보호. 병합·재설치는 별도 확인한다
- [x] 전체 배포 태그 처리 구현과 모조 회귀 검사. 앱 요청 태그와 nginx·렌더러 `latest` 분리. 병합·재설치는 별도 확인한다
- [ ] 게이트 보완본 병합·재설치와 실제 배포 검증
- [ ] 게이트가 헬스 실패로 종료해도 Actions의 원복 단계가 실행되고 `deployed`·`previous` 포인터가 유지되는지 검증
- [x] Actions용 Tailscale OAuth 클라이언트(`tag:ci`) 생성과 `TAILSCALE_OAUTH_CLIENT_ID`, `TAILSCALE_OAUTH_CLIENT_SECRET` 등록
- [x] `MACMINI_SERVER_HOST`, `MACMINI_SERVER_USER`, `MACMINI_SERVER_SSH_KEY` 등록. 기존 `deploy` 레포 clone용 키를 재사용
- [x] 게이트 스크립트, 일일 백업, 개발 스냅샷, 로컬 DB 교체 스크립트의 원본을 레포에 둔다(`infra/prod/`, `infra/local/restore-snapshot.sh`. 설치는 손으로)

### 4. 맥미니 기반

- [x] FileVault 끄기(복호화에 시간이 걸린다)
- [x] `admin` 생성 후 개발 계정을 일반 계정으로 내림. Homebrew는 개발 계정 소유로 두고 `deploy`는 쓰지 않는다
- [x] `deploy` 생성, 홈 `700`
- [x] `pmset` 잠자기 끔, 정전 후 자동 부팅, 개발 계정 자동 로그인, macOS 업데이트 자동 설치 끔
- [x] `deploy`에 Colima 설치(Homebrew 없이 `~/Optional`), LaunchDaemon 등록
- [x] `deploy` 레포 clone(읽기 전용 Deploy key)
- [x] 재부팅 뒤 LaunchDaemon이 `deploy`의 Colima를 띄우는지 확인(개발 계정 자동 로그인은 켠 채로)
- [x] `tailscaled` 데몬 설치(기존 Standalone 앱과 네트워크 확장 제거), 기기 이름 `macmini`, 키 만료 끔
- [x] Tailscale `tag:macmini` 지정과 ACL 적용, 키 만료 끄기와 소유자 SSH 접속 확인
- [x] 원격 로그인은 `admin`, `deploy`만 허용. 비밀번호·root 로그인 끔, `admin`은 소유자 PC 키로 접속
- [x] `marketry-network` 만들기 전에 `172.30.0.0/24`가 Colima VM 경로·Docker 기본 브리지·집 공유기·Tailscale 대역과 겹치지 않는지 확인.
      겹치면 `setup-network.sh`와 `infra/prod/nginx.conf`의 값을 함께 바꾼다
- [x] `marketry-network` 생성. 고정 서브넷으로 생성 완료
- [x] 게이트·백업 설치(`~/Optional/bin`)와 `deploy`의 `authorized_keys`에 강제 명령·`restrict`로 공개키 등록.
      게이트 입력 검사와 루프백 SSH의 일반 명령 차단 확인
- [x] `deploy` 계정의 GHCR 로그인과 앱 `deployed` 이미지 정보 조회. 기존 `CR_PAT` 사용
- [x] 환경 파일·백업 폴더 이동, 게이트·백업 설치본의 새 경로 반영과 구문 검사
- [x] 공유 스냅샷 폴더(`755`)와 로그 폴더 생성, 백업 LaunchDaemon 파일 설치와 plist 검사. 예약 등록은 이관 당일에 한다
- [x] 운영 환경 파일 값 작성과 Compose 설정 검사 통과. 실제 키움·R2 연결도 별도 확인
      **앱은 이관 당일 전까지 띄우지 않는다**(수집·텔레그램 중복)
- [x] R2 활성화, 버킷·업로드용 액세스 키 생성과 `daily/` 10일 보관 규칙 적용
- [x] rclone 설치와 배포본 파일 검사
- [x] 운영 환경 파일의 R2 설정으로 임시 파일 업로드·다운로드·내용 비교·삭제 검증
- [ ] 이관 당일 일일 백업 예약 등록, 실제 덤프·R2 업로드·개발 스냅샷 검증
- [ ] 외부 감시 등록(사용자 요청으로 나중에 진행)
- [x] Claude Code 관리형 설정, Codex `requirements.toml`
- [x] GitHub 인증을 fine-grained PAT로 교체(`gh`와 git 자격증명 모두), 원격 주소 HTTPS, 맥미니 SSH 키는 GitHub에서 삭제
- [x] 개발 계정 소유 앱이 root 서비스로 도는 구멍 점검(RustDesk root 서비스 끔)
- [x] 개발 환경 도구 설치 확인: OrbStack, JDK 21, Node
- [x] 개발 계정의 05:00 스냅샷 복원 LaunchAgent 파일 준비와 plist·구문 검사
- [x] 로컬 실행 스크립트의 옛 레포 경로 수정, 개발 DB·앱 기동 검증과 사용자 화면 확인
- [ ] 첫 스냅샷으로 개발 DB 복원 검증 뒤 05:00 예약 작업 활성화

### 5. 키움 프록시 (오라클 서버 2)

- [x] 서버 2의 현재 공인 IP가 임시 IP임을 확인
- [x] 예약 IP 생성, 콘솔의 사용 가능 상태 확인
- [x] 서버 2 OS(Ubuntu 22.04)와 사용자 SSH 접속 확인
- [x] Tailscale 설치·인증(`--accept-dns=false --ssh=false`), 소유자 기기의 tailnet SSH 접속과 저장된 접속 설정 변경
- [x] 기존 렌더러는 VCN 내부 주소로 연결됨을 확인, `RENDERER_SERVER_HOST`를 예약 IP로 변경
- [x] 기존 임시 IP를 예약 IP로 교체, 발신 IP 일치와 tailnet SSH 접속 확인
- [x] IP 교체 뒤 서버 1에서 현재 렌더러 헬스체크 HTTP 200 응답 확인
- [x] 서버 2 IP를 키움 허용 IP에 추가. 서버 1 IP는 이관이 끝날 때까지 둔다
- [x] tinyproxy 설치, 부팅 시 자동 시작 등록
- [x] tinyproxy 설정: tailnet IPv4에서만 listen, `api.kiwoom.com:443`만 허용. 서버 2에서 연결과 차단 동작 확인
- [x] tinyproxy에 Tailscale 선행 기동과 장애 시 5초 간격 재시작 설정 추가, 적용 확인
- [x] Tailscale `tag:proxy` 지정
- [x] 서버 2의 Tailscale 키 만료 끄기 확인
- [x] Tailscale ACL 정책 저장(위 표), 소유자 기기에서 두 서버의 SSH 접속 확인
- [x] 맥미니에서 프록시를 통한 키움 HTTPS 연결과 다른 도메인·HTTP·다른 포트 차단 확인
- [x] 이관 전 OrbStack과 Colima 컨테이너에서 프록시를 거친 인증·읽기 전용 조회 테스트. `ka20001` 성공 코드와 숫자 데이터 확인
- [ ] 사전 검사 후 기존 운영 토큰·수집 상태에 영향이 없는지 확인

### 6. 이관 당일 (주말)

금요일 장 마감 뒤부터 일요일 사이에 한다. 월요일 장 시작 전까지 못 고치면 되돌린다.

- [ ] 지금 서버 애플리케이션 중지
- [ ] DB 덤프, 맥미니 운영 DB에 적재. 원본은 `market_monitor_db`(사용자 `market_monitor`), 대상은 `marketry_db`(사용자 `marketry`)라
      `pg_dump -Fc` 후 `pg_restore --no-owner --role=marketry`로 넣는다. 이 단계에서는 postgres만 손으로 처음 `up -d` 한다.
      맥미니 cloudflared는 내부 캡처 검사 통과 뒤 터널 전환 단계에서 기동한다
- [ ] 운영 DB 기동 뒤 설치한 백업 LaunchDaemon을 등록하고 덤프·R2 업로드·개발 스냅샷을 확인
- [ ] 맥미니 운영 기동(`MARKETRY_PROXY_HOST`, `MARKETRY_PROXY_PORT` 설정)
- [ ] 공개 터널 전환 전 내부 주소로 실제 앱 화면 캡처와 PNG 내용 검증
- [ ] 지금 서버 cloudflared 중지, 맥미니 cloudflared 기동
- [ ] 터널 경로의 서비스 주소를 맥미니 nginx 컨테이너 이름(`marketry-nginx:80`)으로 바꾼다. 되돌릴 때는 원래 값으로
- [ ] 웹 화면과 로그인 확인
- [ ] 텔레그램 캡처 수동 발송 확인. 실제 채팅방으로 나가니 필요하면 잠시 개발자 채팅방으로 돌린다
- [ ] 키움 토큰 발급이 프록시를 거쳐 되는지 로그로 확인
- [ ] 스케줄러와 수집 확인
- [ ] GitHub Actions로 맥미니에 배포 한 번 돌려 게이트, 덤프, 헬스체크 확인

### 7. 되돌리기

지금 서버는 컨테이너를 내리기만 하고 지우지 않는다. 되돌릴 때는 맥미니 cloudflared를 내리고 지금 서버의
애플리케이션과 cloudflared를 다시 올린다. 이관 뒤 맥미니에 쌓인 데이터는 버린다. 주말이라 거의 없다.

### 8. 안정화 뒤 정리

- [ ] 서버 2 로그인 계정 이름 변경과 SSH 접속 설정 정리
- [ ] 며칠 운영해 본 뒤 오라클 서버 1의 불필요한 서비스 정리. 인스턴스는 재사용할 계획이다
- [ ] 키움 허용 IP에서 서버 1 제거
- [ ] DuckDNS 리디렉트 종료 시점 결정
- [ ] 보관 일수 재조정(DB 크기 기준). 백업 로그로 실제 소요 시간을 보고 04:30 백업과 05:00 개발 DB 복원 간격이 충분한지 확인.
      겹쳐도 스냅샷은 임시 파일에 쓴 뒤 이름을 바꾸므로 복원은 전날 스냅샷을 읽을 뿐 깨지지 않는다
- [ ] nginx `set_real_ip_from`을 맥미니 `marketry-network` 네트워크 대역 하나로 좁힌다. 지금은 대역을 몰라 사설 대역 셋을 다 열어 두었다
- [ ] `operations.md`를 새 구성으로 고쳐 쓰고 이 파일 삭제
- [ ] 오라클 서버 env 파일에서 `OWNER_USER_ID`, `RENDERER_OWNER_CAPTURE_ENABLED`, `CAPTURE_URL` 삭제. 텔레그램 캡처를 확인하기 전까지는
      되돌리기용으로 남겨 둔다(이전 이미지는 이 값을 env에서 읽는다). 맥미니 env에는 처음부터 넣지 않는다
- [ ] 레포에 남은 옛 구성 정리: 오라클용 compose·배포 스크립트·워크플로 단계, `~/repo/market-monitor-backend` 경로,
      DuckDNS·Let's Encrypt nginx 블록과 인증서 설정, 렌더러 3000 포트 공개, `market-monitor-*` 컨테이너·DB 이름 기본값,
      `CLAUDE.md`와 `.claude/settings.json`의 옛 로컬 절대 경로
- [ ] 오라클 렌더러 배포 경로 정리 뒤 GitHub의 `RENDERER_SERVER_HOST`, `RENDERER_SERVER_USER`, `RENDERER_SERVER_SSH_KEY` 정리
- [ ] 이미지 빌드에서 `linux/amd64` 제거 여부 결정. 맥미니 장애 때 클라우드 서버로 급히 옮길 여지를 남기려면 둔다.
      빌드 머신 플랫폼에서 빌드하는 구조(`--platform=$BUILDPLATFORM`)는 Actions 러너가 amd64라 그대로 둔다
- [ ] GitHub 계정 SSH keys에서 오라클 서버 키 삭제(계정 전체 쓰기 권한이 있는 키)
- [ ] 문서·지시서에 남은 실명 흔적 정리: 개발 계정 이름, 컴퓨터 이름이 찍힌 출력. 이후 글에서는 "개발 계정"으로 쓴다.
      GitHub 사용자 이름이 들어간 레포·이미지 주소는 바꿀 수 없으니 그대로 둔다
- [ ] 결정 사항을 `decisions.md`로 회수

---

## 열어둔 것

- 개발용 스냅샷은 이메일, Google `sub`, 닉네임, 프로필 이미지만 가린다. 사용자가 입력한 설정값(섹터 이름, `user_preference.payload`)은
  그대로 둔다. 개인 정보를 적을 수 있는 칸인지 소유자가 판단한다
- Codex는 명령 단위 금지가 없다. 샌드박스와 승인 정책(`requirements.toml`), 지침(`managed_config.toml`의 `developer_instructions`)으로 대신한다.
  지침이 적용되는 것은 확인했다
- 키움이 같은 앱키로 여러 곳에서 토큰을 받을 때 앞선 토큰을 무효화하는지는 모른다. 이관 방식이 지금
  서버를 먼저 내리는 쪽이라 당일에는 문제가 되지 않는다
