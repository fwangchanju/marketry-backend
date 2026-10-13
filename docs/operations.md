# 운영 — 배포 · 롤백 · 장애 대응

배포나 롤백이 필요하면 코드부터 뒤지지 말고 이 문서를 먼저 본다.

서버 이관을 준비하고 있다. 계획과 체크리스트는 `docs/server-migration.md`에 있고, 이관이 끝나기 전까지
이 문서는 지금 구성을 기준으로 한다.

---

## 구성

서버 두 대에 컨테이너 세 종류가 돈다.

| 이미지 | 서버 | 내용 |
|---|---|---|
| `ghcr.io/fwangchanju/market-monitor` | 서버 1 | Spring 애플리케이션 |
| `ghcr.io/fwangchanju/market-monitor-nginx` | 서버 1 | nginx + 프론트 정적 자산이 구워져 있음 |
| `ghcr.io/fwangchanju/market-monitor-renderer` | 서버 2 | 스크린샷 렌더러 |

서비스 주소는 `marketry.co.kr`이다. Cloudflare Tunnel로 받으며, 서버 1의 `market-monitor-cloudflared` 컨테이너가
`market-monitor-nginx:80`으로 넘긴다(`infra/nginx-docker-compose.yml`). 이 경로는 http라 nginx가 `X-Forwarded-Proto`를
`https`로 고정하고, 실제 접속자 IP는 `CF-Connecting-IP`에서 가져온다. 옛 주소 `eolmae.duckdns.org`는 새 주소로 301
리디렉트만 한다.

nginx 이미지는 프론트 레포가 만든 `market-monitor-assets:latest`를 `FROM`으로 가져다 굽는다
(`containers/nginx/Dockerfile`). 프론트 코드는 nginx 이미지 안에 박히므로, 프론트를 배포하려면
nginx 이미지를 다시 빌드해야 한다.

> nginx가 프론트 레포가 아니라 여기 있는 이유는 `docs/decisions.md` 참고.

---

## 이미지 태그 체계

| 태그 | 언제 생기나 |
|---|---|
| `:sha-<커밋SHA>` | 브랜치 push 시 자동 빌드 (승격의 재료) |
| `:main` | main 병합 시, `:sha-<머지된 브랜치 tip>`을 재태깅(재빌드 없음) |
| `:deployed` | 배포 성공 시, 지금 서버에 떠 있는 이미지를 가리키도록 워크플로가 갱신 |
| `:previous` | 배포 성공 시, `:deployed`가 가리키던 직전 정상 이미지로 갱신 |

`:main`은 "배포 후보", `:deployed`는 "실제로 떠 있는 것"이다. 둘이 다르면 병합해놓고 배포를
안 한 상태라는 뜻이다.

`:deployed`/`:previous`는 사람이 손대지 않는다. 롤백이 이 두 개만 보고 동작한다.

### main 브랜치 보호 설정 (전제 조건)

승격 로직이 머지 커밋의 `HEAD^2`(= 병합된 브랜치의 tip)로 원본 이미지를 찾기 때문에, main에 올라오는
커밋이 항상 머지 커밋이어야 한다. GitHub 설정에서 아래가 유지돼야 이 구조가 성립한다.

- Rulesets: Require a pull request before merging (main 직접 push 금지)
- 저장소 Pull Requests 설정: Allow merge commits만 켜고 Squash/Rebase merge는 끈다

---

## 병합과 배포는 분리되어 있다

main에 병합해도 자동으로 배포되지 않는다. 병합 시점에는 `:main` 태그 승격만 일어난다.
실제 배포는 원하는 시점에 수동으로 실행한다.

배포하면 컨테이너가 교체되면서 서비스가 잠시 끊기므로, 장 시간을 피해서 몰아서 하면 된다.

nginx·renderer도 마찬가지로 병합만으로는 안 나간다. 예외는 하나 — 프론트 레포가 자산을 발행하면
(`repository_dispatch`) nginx가 자동으로 다시 빌드·배포된다. 프론트 배포 경로가 그것뿐이라 남겨뒀다.

> 승격(재태깅)은 남겨둬야 한다. 이걸 빼면 `:main` 태그가 안 만들어지고, 수동 배포 시 이미지가 없어서
> 처음부터 다시 빌드하게 된다. 빌드가 두 번 도는 셈이다.

---

## 배포하기

GitHub Actions → Release 워크플로 → `Run workflow`

1. Use workflow from: 항상 `main` — 다른 브랜치를 고를 이유가 없다
2. target: `application` / `nginx` / `renderer` / `all` / `rollback`
3. 실행

`application`은 `:main` 이미지를 그대로 배포한다. 워크플로는 이미지를 빌드하지 않는다. 배포할 태그가
없으면 명확한 메시지와 함께 실패한다(조용히 엉뚱한 걸 배포하지 않는다).

배포 → 헬스체크가 성공하면 `:previous`/`:deployed` 포인터가 자동으로 갱신된다.
헬스체크가 실패하면 워크플로가 `:deployed`를 다시 배포해 원상복구한 뒤 실패로 끝난다. 즉
실패한 배포는 서버에 남지 않는다.

### `application`은 렌더러를 건드리지 않는다

target은 컨테이너 하나만 고른다. `application`을 배포해도 서버 2의 렌더러는 그대로다.
`containers/renderer/`를 고쳤으면 `target=renderer`를 따로 돌려야 나간다.

실제로 2026-09-09까지 렌더러에는 8월 하순 이미지가 떠 있었다. 백엔드만 배포하면서 렌더러 쪽
수정이 반영됐다고 착각하기 쉬우니, `containers/` 아래를 고친 PR을 병합했으면 그 target도 함께
돌렸는지 확인한다.

`all`은 nginx까지 배포한다. `deploy-nginx.sh`가 `down` 후 `up -d`라 화면이 잠깐 안 뜬다.
장 시간이면 `application`과 `renderer`를 나눠 돌리는 편이 낫다. 렌더러는 서버 2에 있고 웹 경로에
끼지 않으므로 아무 때나 배포해도 화면에 영향이 없다.

### 배포 전 확인

병합과 배포가 분리돼 있으므로 "병합했는데 배포를 깜빡" 해서 운영이 main보다 뒤처질 수 있다.
GHCR에서 `:main`과 `:deployed`가 같은 다이제스트인지 보면 바로 안다.

---

## 롤백하기

Release 워크플로 → target에 `rollback` → 실행. 그게 전부다. 무엇으로 되돌릴지 고르지 않는다 —
`:previous`가 직전 정상 이미지를 기억하고 있다.

성공하면 `:deployed`와 `:previous`가 맞바뀐다.

### 알아둘 것 네 가지

1. 첫 롤백은 못 쓴다. `:previous`는 두 번째 프로덕션 배포부터 생긴다. 없으면 실패한다
2. 롤백은 토글이다. 두 번 누르면 방금 되돌린 이미지로 다시 돌아간다. 두 단계 전으로 가는 수단은
   없다
3. 롤백한 뒤에 `application`을 누르면 사고 낸 이미지가 다시 나간다. 롤백은 `:main`을 바꾸지 않기
   때문이다. revert PR을 병합해 `:main`이 갱신된 뒤에 배포한다
4. 롤백은 애플리케이션 이미지만 되돌린다. 서버는 배포할 때마다 `git reset --hard origin/main`으로
   스크립트를 가져오므로 `nginx.conf`·`docker-compose.yml`·배포 스크립트는 최신이 유지된다. 인프라
   변경을 되돌리려면 revert PR이 필요하다

DB 마이그레이션이 끼어 있으면 이미지 롤백만으로는 부족하다. `docs/rules/process.md`의
"DB 마이그레이션 규칙"(하위호환 추가만, 선-머지)을 지켜야 롤백이 안전하다. 이 규칙이 지켜지는 한
스키마는 항상 구버전 코드와 호환된다.

---

## Flyway

스키마 변경은 **새 버전 파일(V2, V3 …)**로 추가한다. 배포할 때 앱이 뜨면서 Flyway가 자동 적용하므로
서버에서 할 일은 없다. 2026-09-26에 그때까지의 변경을 V1 하나로 합쳤다(`docs/decisions.md`).

`docs/rules/process.md`의 마이그레이션 규칙대로 **하위호환 추가만** 하고, 스키마 PR을 먼저 병합·배포한다.
그래야 롤백한 옛 이미지도 새 스키마에서 뜬다.

### 테이블·데이터 삭제는 마이그레이션이 아니라 소유자가 DB에서 직접 한다

CI의 `Migration guard`가 마이그레이션 SQL의 `DROP`·`TRUNCATE`·`DELETE`를 막는다("파괴적 변경은 PR이 아니라 소유자가 DB에서 직접 한다"). 필수 체크라서 우회하지 않는다.
쓰는 코드를 먼저 걷어내 배포한 뒤, 소유자가 `deploy` 계정에서 직접 지운다. 되돌릴 수 없으니 지우기 전에 04:30 백업이 만들어졌는지 확인하고, 하나의 트랜잭션으로 묶는다.

```
docker exec -i marketry-postgres psql -U marketry -d marketry_db -v ON_ERROR_STOP=1 <<'SQL'
BEGIN;
DROP TABLE IF EXISTS <테이블>;
COMMIT;
SQL
```

직접 지우면 `flyway_schema_history`와 앱 시작에는 영향이 없다. 엔티티가 남아 있는 상태에서 테이블을 지우면 `ddl-auto=validate`가 시작을 거부하므로, 엔티티를 지운 이미지를 배포한 뒤에 지운다.

### 이미 적용된 V1을 다시 고쳐야 할 때 (예외)

V1을 고치면 운영 DB의 checksum과 달라져 앱이 기동하지 않는다(`validate-on-migrate`). 순서:

1. PR 병합
2. 서버에서 `~/scripts/flyway-cli.sh repair` — main을 받아 V1 checksum을 새 파일에 맞춘다. **스키마는
   바꾸지 않는다**
3. 없어진 버전 파일의 이력 행을 지운다: `DELETE FROM flyway_schema_history WHERE version = 'N';`
4. 배포
5. V1에서 빠진 테이블·컬럼은 운영 DB에서 SQL로 직접 맞춘다(Flyway는 이미 적용된 V1을 다시 실행하지
   않는다). 옛 코드가 아직 그 테이블을 쓰고 있으면 **배포 뒤에** 실행한다

`flyway-cli.sh info`로 이력을 확인할 수 있다.

---

## 인증과 공개 범위

사이트는 공개돼 있다. nginx는 요청 제한만 걸고(IP당 20r/s·버스트 50, `/api/auth/`는 10r/m·버스트 20)
그대로 넘긴다. 권한은 백엔드(Spring Security)가 판정한다.

| 요청 | 누가 |
|---|---|
| 정적 페이지, 기본 지도·섹터 | 누구나 |
| `/api/custom/**`, `isCustom=true` | 로그인 사용자, 자기 데이터만 |
| `/api/admin/**` | `users.role = ADMIN` |

관리자 권한은 DB에서 직접 준다: `UPDATE users SET role = 'ADMIN' WHERE id = ?;` 역할은 토큰에 담기므로
대상 사용자가 다시 로그인해야 적용된다.

### 필요한 환경변수 (`~/env/market-monitor.env`)

| 변수 | 쓰임 |
|---|---|
| `GOOGLE_CLIENT_ID`, `GOOGLE_CLIENT_SECRET` | Google 로그인 |
| `AUTH_JWT_SECRET` | 토큰 서명 키, 32바이트 이상. **바꾸면 전원 로그아웃** |
| `TUNNEL_TOKEN` | Cloudflare Tunnel 토큰. nginx 배포 때 cloudflared 컨테이너에만 전달한다 |
| `MARKETRY_PROXY_HOST`, `MARKETRY_PROXY_PORT` | 키움 호출만 거치는 HTTP 프록시. 비우면 직접 연결한다(지금 서버는 비워 둔다). 포트 기본값 8888 |

환경변수는 컨테이너를 새로 만들 때만 읽힌다. 파일을 고친 뒤 배포(또는 `docker compose up -d`)해야 반영된다.
로그인 복귀 주소는 `application-prod.properties`의 `marketry.base-url`을 따른다.
비밀이 아닌 운영 설정은 env 파일에 두지 않는다. 소유자 계정(`marketry.owner-user-id`)과 소유자 캡처 여부
(`renderer.owner-capture-enabled`)는 `application-prod.properties`에, 렌더러가 여는 주소(`CAPTURE_URL`)는
`infra/renderer-docker-compose.yml`에 있다.

Google Cloud 콘솔(Google Auth Platform): 앱은 게시(프로덕션) 상태다. 요청 범위가 `openid email profile`
뿐이라 사용자 수 한도와 심사가 없다. 도메인을 바꾸면 리디렉션 URI와 브랜딩 링크를 같이 고친다
(`docs/backlog.md`).

### 롤백할 때

2026-09-26 이전 이미지(IP 화이트리스트 시절)로는 되돌릴 수 없다. 그 코드가 쓰던 테이블
(`allowed_ip`, `admin_token` 등)을 지웠기 때문에 `validate`에서 기동이 실패한다.

---

## 헬스체크

배포 직후 `infra/scripts/health-check.sh`가 `/actuator/health`를 폴링한다.

컨테이너가 `restart: always`라 크래시해도 즉시 재시작되어 "Exited" 상태를 잡기 어렵다. 그래서
`docker inspect`의 `RestartCount`가 3 이상이면 크래시 루프로 간주하고 타임아웃(10분) 전에 조기 실패
처리한다. 실패하면 `docker logs`를 출력하므로 서버에 SSH로 들어가지 않고 Actions 로그에서 원인을
확인할 수 있다.

nginx는 `infra/scripts/nginx-health-check.sh`로 RestartCount만 확인한다(타임아웃 30초). nginx는
config 문법 오류면 즉시 기동 실패하는 이진적 실패 모드라 짧아도 충분하다.

---

## 이미지 정리

- **서버 로컬**: 배포 스크립트가 매번 "실행 중인 것 + 최근 2개"만 남기고 삭제한다.
  서버 디스크가 넉넉하지 않아 타이트하게 잡았다
- **GHCR**: 앱 이미지는 태그가 붙은 최근 20개만 유지한다. `:main`, `:deployed`, `:previous`는 개수와 상관없이
  지우지 않고, 어느 태그에도 연결되지 않은 매니페스트만 지운다(`dataaxiom/ghcr-cleanup-action`)

이미지는 amd64와 arm64를 한 태그에 같이 담는다. 서버의 `docker pull`은 자기 아키텍처를 골라 받는다.
플랫폼별 매니페스트는 태그 없이 따로 저장되기 때문에, 버전 개수만 세는 정리 도구를 쓰면 살아 있는 이미지의
일부가 지워져 `:main`이나 `:previous`가 깨진다. 정리 도구를 바꿀 때 이 점을 확인한다.

---

## 프론트 배포

프론트 레포에서 Release 워크플로를 실행하면 `market-monitor-assets:latest`가 갱신되고,
`repository_dispatch`로 이 레포의 `nginx` job이 트리거되어 nginx 이미지를 다시 빌드하고 배포한다.
이건 자동으로 유지한다.

> nginx job은 태그 존재 여부로 재빌드를 건너뛰던 로직이 있었는데, 그 태그가 프론트 자산의 변경을
> 반영하지 못해서 프론트를 배포해도 운영에 안 나가는 버그가 있었다. 지금은 매번 재빌드한다.
> 자세한 경위는 `docs/decisions.md`.

---

## 장애 시 확인 순서

1. `/actuator/health`: 앱이 떠 있는가
2. GitHub Actions의 마지막 배포 로그: 헬스체크가 실패했다면 `docker logs`가 찍혀 있다
3. `logs/exception.log`: **throwable이 붙은 로그가 전부 여기 쌓인다.** `EscalateException`으로 분류된
   장애뿐 아니라 400/404/409, 알림이 억제된 반복 예외, 알림 발송 실패 자체까지 들어온다. 예외를
   안 붙이고 메시지만 찍는 로그는 안 들어오므로, 여기 없다고 문제가 없었다는 뜻은 아니다.
   전체 흐름은 `logs/application.log`를 본다
4. 텔레그램 개발자 채널(`DEVELOPER_CHAT_ID`): 에스컬레이션 알림이 갔는지
5. 수집 실패라면 `CollectionScheduler` 로그: 수집기별 시작/종료와 소요 시간이 찍힌다

알림이 안 왔다고 장애가 없는 건 아니다. 텔레그램 자체가 죽으면 알림 경로도 같이 죽는다.

### 로그 파일

`prod` 프로파일에서만 파일로 남는다. 호스트의 `${LOG_DIR}`가 컨테이너의 `/app/logs`에 마운트된다.

| 파일 | 내용 | maxFileSize | maxHistory | totalSizeCap |
|---|---|---|---|---|
| `application.log` | 전부 (INFO 이상) | 100MB | 7 | 1GB |
| `exception.log` | throwable이 붙은 것만 | 50MB | 30 | 500MB |

상한은 평소 사용량이 아니라 **폭주했을 때 잃어도 되는 양**으로 잡았다. 정상 운영에서는 닿지 않는다.

굴러간 파일은 `application.2026-09-14.0.log` 꼴이다. 청소는 이 패턴에 맞는 파일만 대상으로 하므로,
패턴을 바꾸면 그 이전 파일은 영구히 남는다. 패턴을 손볼 일이 생기면 옛 파일을 직접 지운다.

---

## 서버 접근

배포 스크립트는 `infra/scripts/`에 있고, 서버에서 매 배포마다
`git fetch && git reset --hard origin/<브랜치>`를 수행한다.

이건 앱 코드를 가져오는 게 아니다. 앱 코드는 Docker 이미지 안의 jar가 100% 결정한다. 이 체크아웃은
오직 배포 스크립트와 `docker-compose.yml`을 최신으로 유지하기 위한 것이다.
