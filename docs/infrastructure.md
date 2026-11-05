# 맥미니 운영 구성

운영은 오라클 클라우드 서버 두 대에서 집의 맥미니(M4, RAM 24GB, SSD 512GB) 한 대로 옮겼다. 맥미니는 운영 서버이면서 개발 환경이기도 해서,
구성의 대부분은 개발 쪽 에이전트가 실수로도 운영을 건드리지 못하게 하고 사고가 나면 되돌릴 수 있게 하는 데 쓰인다.
이관은 끝났고 이 문서는 현재 구성의 설명이다. 배포와 롤백은 `operations.md`, 설치 순서는 `infra/prod/README.md`를 본다. 옛 이관 계획과 체크리스트는 지웠고 git 기록에 남아 있다.

## 계정

| 계정 | 종류 | 쓰임 |
|---|---|---|
| 개발 계정 | 일반 | 사람과 에이전트가 모두 여기서 작업한다. 러스트데스크 세션도 이 계정이다 |
| `deploy` | 일반 | 운영 컨테이너, 운영 DB 볼륨, 환경변수, 백업을 소유한다. GitHub Actions가 SSH로 들어오는 계정 |
| `admin` | 관리자 | 비밀번호는 소유자만 안다. 로그인하지 않고 `su - admin`이나 인증 창에만 쓴다 |

개발 계정을 관리자로 두고 sudo 비밀번호로만 막는 안도 검토했다. 에이전트가 인증 창을 띄우면 비개발자가
비밀번호를 넣을 수 있어서 접었다. 경계는 사람의 행동이 아니라 OS 권한이어야 한다.

`deploy` 홈은 `700`이다. 개발 계정에서는 운영 파일을 읽을 수도 운영 Docker에 붙을 수도 없다.

## 컨테이너 런타임

운영은 `deploy` 계정의 Colima(vz, CPU 4, 메모리 6GB, 디스크 100GB)로 돌리고 부팅 시 LaunchDaemon으로
뜨게 한다. 아무도 로그인하지 않아도 운영이 올라와야 해서다. OrbStack은 GUI 로그인이 필요하고 여러 계정이
동시에 쓸 수 없어 운영에는 못 쓴다.

개발은 개발 계정의 OrbStack(메모리 상한 8GB)이다. 쓰지 않는 메모리를 macOS에 돌려줘서 개발용으로는
이쪽이 낫다.

운영 Colima 안에는 PostgreSQL, 애플리케이션, nginx, 렌더러, cloudflared가 같은 네트워크로 뜬다.
호스트에 여는 포트는 없다. 개발 쪽 포트(DB `15432`, 백엔드 `18081`, 프론트 `5173`)와 부딪힐 일이 없다.

### 맥미니에 실제로 구성한 것 (2026-10-04)

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

### 맥미니 관리 접속과 에이전트 제한 (2026-10-05)

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
이관 전 서버에서 평일 캡처를 거친 뒤 잰 최댓값(`memory.peak`)이 약 558MB였고 그 1.5~2배로 잡았다.
DB와 애플리케이션에는 걸지 않는다. 제한에 걸려 DB나 수집 중인 앱이 죽는 쪽이 더 나쁘다.

## 인바운드

새 도메인을 사서 Cloudflare DNS에 올리고 Cloudflare Tunnel로 받는다. 공유기 포트포워딩, DuckDNS IP 갱신,
Let's Encrypt는 쓰지 않는다. 맥미니가 바깥으로 연결을 여는 구조라 유동 IP 변경, 통신사 변경, 정전 후
재부팅, CGNAT의 영향을 받지 않는다. 집 IP도 드러나지 않는다.

포트포워딩과 DuckDNS를 쓰는 안도 검토했다. 집 IP가 공개되고, 공격을 받으면 집 인터넷 전체가 멈추고,
통신사 포트 차단과 NAT 루프백을 따로 확인해야 해서 접었다.

터널은 이관보다 먼저 옛 서버에 붙여 도메인 전환을 끝냈다. 그래서 이관 당일의 전환은 cloudflared를
어느 쪽에서 띄우느냐만 바꾸면 됐다. 도메인 변경과 서버 변경이 한날에 겹치지 않았다.

렌더러는 nginx 컨테이너에 서비스 도메인을 네트워크 별칭으로 붙여 내부로 바로 접근한다.

## 아웃바운드(키움, 토스)

키움 REST API는 허용 IP로만 호출된다. 맥미니는 유동 IP라서 키움 호출만 지금 렌더러가 도는 오라클 서버 2를
거친다. 서버 2에 tinyproxy를 두고 tailnet 주소에서만 받으며 `api.kiwoom.com:443`만 통과시킨다.

애플리케이션은 `kiwoomRestClient` 빈에만 프록시를 건다(`MARKETRY_PROXY_HOST`, `MARKETRY_PROXY_PORT`).
값이 비면 지금처럼 직접 연결하므로 이관 전에 배포해도 영향이 없다. 토큰 발급과 API 호출이 모두 이 빈을
쓴다. 키움 API 인증 환경변수는 `KIWOOM_APP_KEY`, `KIWOOM_APP_SECRET`을 쓴다. 이관 때는 `_B` 이름을 유지했고 키 교체와 함께 바꿨다.

토스(`openapi.tossinvest.com:443`)도 허용 IP로만 호출된다. 그래서 `tossRestClient` 빈이 같은 프록시 설정을 쓰고, 서버 2의 tinyproxy는 이 주소도 통과시켜야 한다. 토스 호출은 처음에는 프록시 없이 맥미니에서 직접 나갔고 이 변경으로 바꿨다.

서버 1이 아니라 서버 2를 프록시로 쓰는 건 이관 중 문제가 생기면 서버 1을 그대로 다시 올리기 위해서다.
오라클 유휴 회수는 수년간 겪지 않아 따로 대비하지 않는다. 회수되면 종량제로 바꾸거나 다른 곳의 최소
사양 서버에 tinyproxy와 Tailscale을 올리고 키움 허용 IP와 `MARKETRY_PROXY_HOST`만 바꾼다.

## Tailscale

| 기기 | 태그 | 허용 |
|---|---|---|
| 소유자 노트북 | 없음(소유자 계정) | 모든 기기 |
| 맥미니 | `tag:macmini` | `tag:proxy`의 8888만 |
| 오라클 서버 2 | `tag:proxy` | 없음 |
| GitHub Actions | `tag:ci` (임시 노드) | `tag:macmini`의 22만 |

Tailscale SSH는 켜지 않는다. 기기 인증만으로 SSH가 열리면 계정 분리가 무의미해진다. 서버 기기는
태그를 달아 키 만료를 끈다. 맥미니는 App Store 앱 대신 `tailscaled`를 부팅 데몬으로 설치한다.
로그인 없이 연결되고 개발 계정이 끌 수 없다.

## 배포

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

## 가용성과 백업

시스템 잠자기를 끄고(`pmset -a sleep 0`) 정전 후 자동 부팅을 켠다(`pmset -a autorestart 1`).
FileVault는 끈다. 켜져 있으면 재부팅 뒤 잠금 해제 화면에서 멈춰 운영, Tailscale, 터널이 모두 안 뜬다.
개발 계정으로 자동 로그인하고, macOS 업데이트는 자동 다운로드만 하고 주말에 손으로 설치한다.

맥미니나 집 인터넷이 죽으면 앱의 텔레그램 알림도 같이 죽는다. 그래서 외부 감시 서비스(UptimeRobot 등)가
5분마다 헬스체크를 부르게 한다.

`deploy`의 예약 작업이 매일 04:30에 `pg_dump`를 뜬다. 맥미니에 7일, Cloudflare R2에 10일을 남긴다.
R2 보관 기간은 압축 덤프 크기를 확인한 뒤 무료 사용량의 여유를 고려해 정했다. `daily/` 접두사에 10일 뒤 삭제하는 수명 주기 규칙을 적용한다.
배포 직전 덤프는 맥미니에 최근 5개를 남긴다. DB 크기가 늘면 저장량을 확인해 보관 기간을 재검토한다.

## 로컬 개발

DB만 OrbStack 컨테이너로 띄우고 백엔드와 프론트는 손으로 띄운다. 백엔드는 `local` 프로파일에
`auth.dev-login.enabled=true`, `scheduling.enabled=false`로 뜬다. 텔레그램과 키움 키는 개발 환경에 두지
않는다.

`deploy`가 매일 백업 직후 개발용 스냅샷을 만들어 `/Users/chanju/Projects/MARKETRY/db-dump/`에 둔다. 개발 계정은
읽기만 한다. 스냅샷은 `users` 이메일을 가리고 `user_refresh_token`은 `--exclude-table-data`로 행만 뺀다.
테이블까지 빼면 엔티티 검증과 dev-login이 깨진다. `users`를 통째로 빼는 안은 커스텀 테이블의 외래키가
깨져서 접었다.

개발 계정의 예약 작업이 05:00에 로컬 DB를 지우고 스냅샷으로 다시 만든다. 같은 일을 하는 수동 명령도 둔다.
브랜치에만 있는 마이그레이션은 다음 `bootRun` 때 Flyway가 적용한다.

## 이름

이관하면서 프로젝트 이름을 `marketry`로 통일했다. 서버 경로, 컨테이너, compose, DB, 이미지는 어차피 맥미니에서
새로 만들기 때문에 처음부터 새 이름으로 만들면 추가 비용이 거의 없다. 이관을 끝낸 뒤에 바꾸면 방금 만든 것을
운영 중에 다시 고쳐야 해서 더 번거롭다.

| 대상 | 옛 이름 | 현재 이름 |
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
