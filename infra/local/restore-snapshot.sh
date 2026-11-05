#!/bin/bash
# 운영에서 만든 개발용 스냅샷으로 로컬 개발 DB를 통째로 다시 만든다(drop, create, restore).
# 개발 계정에서 손으로 실행하거나 05:00 LaunchAgent(launchd/kr.co.marketry.dev-snapshot-restore.plist)가 실행한다.
# 로컬 DB에 쌓아 둔 데이터와 웹에서 바꾼 설정은 모두 사라진다. 브랜치에만 있는 마이그레이션은 다음 bootRun 때 Flyway가 적용한다.
set -euo pipefail

SNAPSHOT="${SNAPSHOT_FILE:-/Users/chanju/Projects/MARKETRY/db-dump/latest.dump}"
DB_NAME="marketry_db"
DB_USER="marketry"

LOCAL_DIR="$(cd "$(dirname "$0")" && pwd)"

# 운영 Colima와 접속 대상을 공유하지 않는다. cron/셸의 Docker·Compose override도 받지 않는다.
local_docker() {
  env -u DOCKER_HOST -u DOCKER_CONTEXT -u COMPOSE_FILE -u COMPOSE_PROJECT_NAME \
    -u COMPOSE_PROFILES -u COMPOSE_ENV_FILES -u COMPOSE_PATH_SEPARATOR \
    docker --context orbstack "$@"
}

local_compose() {
  local_docker compose --project-name marketry-local \
    --project-directory "$LOCAL_DIR" -f "$LOCAL_DIR/compose.yml" \
    --env-file "$LOCAL_DIR/.env" "$@"
}

if [ ! -r "$SNAPSHOT" ]; then
  echo "스냅샷이 없습니다: $SNAPSHOT"
  echo "운영 쪽 백업 작업이 아직 스냅샷을 만들지 않았거나 읽기 권한이 없습니다."
  exit 0
fi

# named context가 다른 데몬을 가리키면 DB 생성·삭제 전에 중단한다.
endpoint=$(local_docker context inspect orbstack --format '{{.Endpoints.docker.Host}}')
if [ "$endpoint" != "unix://$HOME/.orbstack/run/docker.sock" ]; then
  echo "중단: orbstack context가 이 개발 계정의 OrbStack 소켓을 가리키지 않습니다."
  exit 1
fi

# 비밀번호는 컨테이너 안의 로컬 소켓 접속으로 처리한다. 환경 파일은 Compose만 읽는다.
db() {
  local_compose exec -T db "$@"
}

echo "로컬 DB 컨테이너 확인"
local_compose up -d --wait db

# 덤프가 손상됐으면 기존 개발 DB를 지우지 않는다.
db pg_restore --list <"$SNAPSHOT" > /dev/null

echo "$DB_NAME 다시 만들기"
db psql -U "$DB_USER" -d postgres -v ON_ERROR_STOP=1 -q \
  -c "DROP DATABASE IF EXISTS $DB_NAME WITH (FORCE)" \
  -c "CREATE DATABASE $DB_NAME OWNER $DB_USER"

echo "스냅샷 복원: $SNAPSHOT"
db pg_restore -U "$DB_USER" -d "$DB_NAME" --no-owner --no-privileges --exit-on-error --single-transaction <"$SNAPSHOT"

echo "완료"
