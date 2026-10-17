# 맥미니 운영 구성

운영 컨테이너 구성과 배포 게이트·백업 스크립트의 원본이다. 맥미니 구성과 결정은 `docs/server-migration.md`에 있다.
아래는 `deploy` 계정에서 하는 작업이고, 표시한 곳만 `admin`이 한다. 오라클 서버용 파일(`infra/*.yml`, `infra/scripts/`)은 이관 당일까지 그대로 쓴다.

## 설치 순서

1. 네트워크를 만든다. 서브넷이 `nginx.conf`의 `set_real_ip_from`과 같아야 한다.

   ```sh
   cd ~/Projects/marketry/marketry-backend/infra/prod
   ./setup-network.sh
   ```

2. env 파일을 만든다. `env.template`의 키를 `~/Projects/marketry/env/marketry.env`로 복사해 값을 채운다.
   `env` 폴더 권한은 `700`, 파일 권한은 `600`으로 둔다. `deploy` 홈의 경로이며 소스 레포 밖이다.
   `R2_*`는 비워 두면 외부 업로드만 건너뛴다.

3. GHCR에 로그인한다(읽기 권한 토큰).

   ```sh
   docker login ghcr.io
   ```

4. 게이트와 백업 스크립트를 레포 밖에 설치한다. 스크립트를 고치면 다시 설치해야 반영된다.

   ```sh
   install -m 755 gate.sh ~/Optional/bin/marketry-gate
   install -m 755 backup.sh ~/Optional/bin/marketry-backup
   mkdir -p ~/Projects/marketry/backups/predeploy ~/Projects/marketry/backups/daily
   chmod 700 ~/Projects/marketry/backups ~/Projects/marketry/backups/predeploy ~/Projects/marketry/backups/daily
   ```

5. `~/.ssh/authorized_keys`에 배포 키를 강제 명령과 함께 등록한다. 현재는 `deploy`의 레포 clone용 키를 재사용한다.

   ```
   command="/Users/deploy/Optional/bin/marketry-gate",restrict ssh-ed25519 AAAA... ci-deploy
   ```

6. 백업 LaunchDaemon을 `admin`이 설치한다.

   ```sh
   sudo install -m 644 -o root -g wheel launchd/kr.co.marketry.backup.plist /Library/LaunchDaemons/
   sudo /usr/bin/plutil -lint /Library/LaunchDaemons/kr.co.marketry.backup.plist
   ```

   이관 당일 운영 DB를 기동한 뒤 예약 작업을 등록한다.

   ```sh
   sudo launchctl bootstrap system /Library/LaunchDaemons/kr.co.marketry.backup.plist
   ```

7. 개발용 스냅샷 폴더를 만든다. `deploy`가 쓰고 개발 계정은 읽기만 한다.

   ```sh
   mkdir -p /Users/Shared/marketry-dev-snapshot
   chmod 755 /Users/Shared/marketry-dev-snapshot
   ```

8. 이관 당일 첫 기동은 사용자가 손으로 한다. 먼저 PostgreSQL만 띄우고 기존 앱 중지와 DB 덤프·복원을 진행한다.

   ```sh
   MARKETRY_ENV_FILE="$HOME/Projects/marketry/env/marketry.env" \
     docker compose -f compose.yml --env-file "$HOME/Projects/marketry/env/marketry.env" up -d marketry-postgres
   ```

   DB 복원 뒤 앱·nginx·렌더러를 띄운다. 이미지 태그는 환경변수로 넘기고 생략하면 `deployed`(앱)와 `latest`(나머지)를 쓴다.

   ```sh
   MARKETRY_ENV_FILE="$HOME/Projects/marketry/env/marketry.env" \
     docker compose -f compose.yml --env-file "$HOME/Projects/marketry/env/marketry.env" \
       up -d marketry-app marketry-nginx marketry-renderer
   ```

   공개 터널은 오라클에 둔 채 내부 캡처를 검사한다. 통과한 뒤 기존 cloudflared를 중지하고 터널 서비스 주소를 맥미니용으로 바꿔 기동한다.

   ```sh
   MARKETRY_ENV_FILE="$HOME/Projects/marketry/env/marketry.env" \
     docker compose -f compose.yml --env-file "$HOME/Projects/marketry/env/marketry.env" up -d marketry-cloudflared
   ```

   이후 배포는 GitHub Actions가 게이트를 거쳐서만 한다.

## GitHub Actions 연결과 활성화

Release를 `main`에서 수동 실행하고 `server=macmini`, `target=connection-check`를 고르면
Tailscale·SSH·강제 명령 게이트를 검사한다. 배포가 아닌 고정 명령을 게이트가 거부하는지 확인하므로
운영 DB 덤프나 컨테이너 기동, 이미지 포인터 변경은 하지 않는다. 이미 등록된 맥미니·Tailscale 시크릿을 사용한다.

수동 실행의 `server` 기본값은 `macmini`다. `oracle`도 직접 선택할 수 있다.
일반 배포는 기존 DB를 유지하며 백업 다운로드나 DB 복원을 실행하지 않는다.
서버 선택·이관 잠금용 Actions 변수는 사용하지 않는다. 인증용 Secrets는 유지한다.
배포 전에는 실제 이관·서비스 상태를 확인한다. 문서만 보고 첫 설치·DB 복원을 반복하지 않는다.

프론트 자산 발행 이벤트는 맥미니 nginx를 한 번 빌드·배포한다. 수동 실행의 서버 입력이 없거나
잘못되면 실패한다. 프론트에서 서버를 선택해 이벤트로 전달하는 기능과 오라클 비상 복구·맥미니 복귀는
후속 작업이며 아직 구현하지 않았다. 기존 오라클 일반 배포와 amd64 이미지 지원은 유지한다.

맥미니 `all`은 nginx·renderer 빌드 뒤 게이트를 한 번 호출한다. 앱의 이미지 다이제스트와 원복 후보는
배포 전에 고정한다. 게이트가 실패하면 일반 앱 배포는 기존 deployed 앱으로 원복하고 포인터를 유지한다.
nginx·renderer 단독 배포 실패와 rollback 실패는 자동 원복하지 않는다.

운영 접근 없이 runner 로직과 워크플로 분기를 검사한다.

```sh
/bin/bash infra/prod/tests/macmini-release-test.sh
/bin/bash infra/prod/tests/restore-snapshot-test.sh
```

## 개발 계정

개발 DB는 `infra/local/restore-snapshot.sh`로 스냅샷에서 다시 만든다. 기존 개발 DB 전체를 교체하므로
첫 실행 전 개발 DB를 별도로 백업한다. 스크립트는 개발 계정의 OrbStack 소켓과 `market-monitor-local`
Compose 프로젝트만 사용하고 Docker·Compose 접속 대상을 바꾸는 상속 변수를 제거한다.
덤프 목록 검사 실패 시 기존 DB를 유지한다. 복원 실패 시 일회성 백업으로 개발 DB를 되돌린다.
스냅샷이 없으면 종료 코드 0으로 건너뛰므로 실제 완료 로그와 마스킹·토큰 제거를 확인해야 한다.
첫 실제 복원을 검증한 뒤 05:00 예약을 활성화한다. 예약 작업은
`infra/local/launchd/kr.co.marketry.dev-snapshot-restore.plist`를 개발 계정의 `~/Library/LaunchAgents`에 복사해 쓴다.

## 키움 프록시 사전 검사

개발 계정에서 `./gradlew kiwoomProxyCheckJar`로 독립 검사 프로그램을 만든다. 만들어진
`build/libs/kiwoom-proxy-check.jar`는 사용자가 `deploy` 소유의 `~/Optional/kiwoom-proxy-check.jar`로 복사한다.
사용자는 개발 계정의 OrbStack에서 먼저 검증한 뒤 `deploy`의 Colima 운영 네트워크에서 같은 JAR로 검사한다.
에이전트는 운영 환경 파일을 읽거나 아래 명령을 실행하지 않는다. 사용자가 `deploy` 계정에서 실행한다.
실제 토큰 발급에 앞서 기존 운영 토큰에 미치는 영향을 확인한다.

```sh
env -u DOCKER_CONTEXT -u DOCKER_CONFIG "$HOME/Optional/bin/docker" \
  --host "unix://$HOME/.colima/default/docker.sock" run --rm --network marketry-network \
  -e MARKETRY_KIWOOM_TOKEN_TEST=1 \
  --mount "type=bind,src=$HOME/Optional/kiwoom-proxy-check.jar,dst=/app/check.jar,readonly" \
  --mount "type=bind,src=$HOME/Projects/marketry/env/marketry.env,dst=/run/marketry.env,readonly" \
  eclipse-temurin:21-jre-noble \
  java -jar /app/check.jar --env-file /run/marketry.env
```

토큰 발급 뒤 지수기여도 수집기에서 사용하는 `ka20001`의 코스피 지수 조회를 한 번만 실행한다.
연속조회·재시도·DB 저장·수집기·텔레그램 발송은 하지 않는다. 이 API에는 날짜 입력이 없으므로 지정일 조회가 아니다.
프로그램은 환경 파일의 키움 인증·프록시 네 변수만 사용한다. 발급 허용은 프로세스 변수로만 받는다.
응답의 성공 코드와 숫자 지수값을 검사하고 결과만 출력한다. 실패 시 종료코드는 1이며 토큰과 응답 원문은 출력하지 않는다.

## 게이트 검증과 이미지 보관

`deploy all <tag>`는 앱에 요청한 태그, nginx·렌더러에 `latest`를 적용한다. 개별 서비스 배포는 요청한 태그를 적용한다.
대상 전체의 헬스체크가 성공하면 요청한 서비스의 오래된 로컬 이미지를 정리한다.
현재·직전 성공 이미지, 생성 시각 기준 최신 고유 이미지 2개, 다른 컨테이너가 사용하는 이미지는 보호한다.
태그 없는 이미지는 게이트가 해당 레포에서 추적한 ID만 정리한다. 상태는 `~/.marketry-gate-images`에 기록한다.
조회·기록 실패 시 정리를 건너뛰고 삭제 실패 시 경고를 남긴다. 강제 삭제는 하지 않는다.

운영 Docker·환경 파일을 사용하지 않는 회귀 검사는 개발 계정에서 실행한다.

```sh
/bin/bash infra/prod/tests/gate-test.sh
```
