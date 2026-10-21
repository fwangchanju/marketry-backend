#!/bin/bash
# 기존 개발 DB를 보존하면서 새 Compose 프로젝트, DB, 사용자 이름으로 복원한다.
set -euo pipefail

if [ "$#" -ne 1 ] || [ ! -s "$1" ]; then
  echo '사용법: rename-db.sh <검증한 개발 DB 덤프 파일>' >&2
  exit 1
fi
backup=$1
local_dir=$(cd "$(dirname "$0")" && pwd)
old_container=market-monitor-local-db-1
new_container=marketry-local-db-1
new_volume=marketry-local_postgres_data
old_stopped=0
completed=0

local_docker() {
  env -u DOCKER_HOST -u DOCKER_CONTEXT -u COMPOSE_FILE -u COMPOSE_PROJECT_NAME \
    -u COMPOSE_PROFILES -u COMPOSE_ENV_FILES -u COMPOSE_PATH_SEPARATOR \
    docker --context orbstack "$@"
}

new_compose() {
  local_docker compose --project-name marketry-local \
    --project-directory "$local_dir" -f "$local_dir/compose.yml" \
    --env-file "$local_dir/.env" "$@"
}

rollback() {
  if [ "$completed" -eq 0 ]; then
    echo '전환 실패: 새 개발 DB를 중지하고 기존 개발 DB를 유지합니다.' >&2
    new_compose down 2>/dev/null || true
    if [ "$old_stopped" -eq 1 ]; then
      local_docker start "$old_container" >/dev/null || true
    fi
  fi
}
trap rollback EXIT

endpoint=$(local_docker context inspect orbstack --format '{{.Endpoints.docker.Host}}')
if [ "$endpoint" != "unix://$HOME/.orbstack/run/docker.sock" ]; then
  echo '중단: 개발 계정의 OrbStack 소켓이 아닙니다.' >&2
  exit 1
fi
if [ ! -f "$local_dir/.env" ]; then
  echo '중단: 개발 DB 설정 파일이 없습니다.' >&2
  exit 1
fi
if lsof -nP -iTCP:18081 -sTCP:LISTEN | awk 'NR>1 {found=1} END {exit !found}'; then
  echo '중단: 로컬 백엔드가 실행 중입니다. 백엔드를 종료한 후 다시 실행하세요.' >&2
  exit 1
fi
if lsof -nP -iTCP:15433 -sTCP:LISTEN | awk 'NR>1 {found=1} END {exit !found}'; then
  echo '중단: 검증용 로컬 포트 15433이 사용 중입니다.' >&2
  exit 1
fi
if [ "$(local_docker inspect --format '{{.State.Running}}' "$old_container")" != true ]; then
  echo '중단: 기존 개발 DB가 실행 중이지 않습니다.' >&2
  exit 1
fi
if local_docker inspect "$new_container" >/dev/null 2>&1; then
  echo '중단: 새 개발 DB 컨테이너가 이미 있습니다. 상태를 먼저 확인하세요.' >&2
  exit 1
fi

local_docker exec -i "$old_container" pg_restore --list < "$backup" >/dev/null
old_members=$(local_docker exec "$old_container" psql -U market_monitor -d market_monitor_db -Atqc 'SELECT count(*) FROM users')
echo "기존 개발 DB 회원 수: $old_members"

existing_volume=$(local_docker volume inspect "$new_volume" --format '{{.Name}}' 2>/dev/null || true)
echo '새 개발 DB를 임시 포트 15433에서 시작합니다.'
LOCAL_DB_PORT=15433 new_compose up -d --wait db
if [ "$existing_volume" = "$new_volume" ]; then
  echo '이전 실행에서 만든 새 볼륨이 있어 복원된 데이터를 확인합니다.'
else
  local_docker exec -i "$new_container" pg_restore -U marketry -d marketry_db \
    --no-owner --no-privileges --exit-on-error --single-transaction < "$backup"
fi
new_members=$(local_docker exec "$new_container" psql -U marketry -d marketry_db -Atqc 'SELECT count(*) FROM users')
if [ "$old_members" != "$new_members" ]; then
  echo "중단: 회원 수가 다릅니다. 기존=$old_members, 새 DB=$new_members" >&2
  exit 1
fi
echo "복원 확인: 회원 ${new_members}명"

echo '기존 개발 DB를 중지하고 새 DB를 기본 로컬 포트로 전환합니다.'
local_docker stop "$old_container" >/dev/null
old_stopped=1
LOCAL_DB_PORT=15432 new_compose up -d --wait --force-recreate db
final_members=$(local_docker exec "$new_container" psql -U marketry -d marketry_db -Atqc 'SELECT count(*) FROM users')
if [ "$final_members" != "$old_members" ]; then
  echo '중단: 기본 포트 전환 뒤 회원 수가 다릅니다.' >&2
  exit 1
fi
completed=1
echo "개발 DB 이름 전환 완료: marketry-local / marketry_db / marketry (회원 ${final_members}명)"
echo '기존 개발 DB 컨테이너와 볼륨은 보존했습니다.'
