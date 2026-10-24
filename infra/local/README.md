# 로컬 DB 환경

PostgreSQL만 Docker에서 실행한다. 백엔드는 설치된 Java 21로, 프론트엔드는 Vite로 실행한다.
상위 폴더의 `local/start-db.command`, `start-backend.command`,
`start-frontend.command`를 차례로 더블클릭하면 된다.
Compose 프로젝트명은 `marketry-local`이다. DB는 전용 named volume에 저장된다.

## 시작

```sh
cd marketry-backend/infra/local
cp ../env.template .env  # 최초 1회만. 로컬 DB 비밀번호와 포트만 채우고 나머지는 비워 둔다
# 상위 폴더의 local/start-db.command 더블클릭
# 상위 폴더의 start-backend.command 더블클릭
# 상위 폴더의 start-frontend.command 더블클릭
```

프론트엔드의 `/api` 요청은 백엔드 `infra/local/.env`의 `LOCAL_API_PORT`로 전달된다.
포트를 바꾸면 두 실행 창을 재시작한다. 프론트엔드 의존성이 없거나 변경되면
실행 파일이 `npm ci`를 먼저 수행한다.

DB에 호스트에서 접속할 때는 `localhost:15432`, 컨테이너 안에서는 `db:5432`를 사용한다.
DB 포트는 `LOCAL_DB_PORT`로 변경할 수 있다. Flyway가 백엔드 시작 시 스키마를 적용한다.
화면 확인용 데이터는 사용자가 운영 DB에서 덤프를 떠서 이 로컬 DB에 직접 넣는다.
백엔드는 로컬 DB를 읽기 때문에 덤프를 넣은 뒤에는 수집기를 실행할 필요가 없다.
DB는 named volume에 유지되므로 재시작해도 가져온 데이터와 웹에서 바꾼 설정이 남는다.

## 로컬 환경 정책

- 예약 수집·텔레그램 발송·스냅샷 정리는 `prod` 프로필에서만 등록된다. `local` 프로필은 `scheduling.enabled=false`도 지정한다.
- 현재 백엔드가 항상 빈 응답을 주는 장중 투자자 랭킹·종목별 이력은 DB에 데이터가 있어도 빈 화면이다.
- `.env`에는 로컬 DB 암호를 넣는다. 외부 API 자격 증명은 화면 확인에 필요 없다. 이 파일은 Git에서 제외된다.
- 백엔드와 DB 포트는 로컬 호스트에만 공개된다. 운영 배포용 `../docker-compose.yml`에는 영향을 주지 않는다.
- `docker compose down`은 컨테이너만 내린다. DB 데이터를 지우려는 경우에만 `docker compose down -v`를 사용한다.
