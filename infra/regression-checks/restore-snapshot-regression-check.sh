#!/bin/bash
# 개발 복원의 대상 격리·삭제 전 검증을 실제 Docker/환경 파일 없이 검사한다.
set -euo pipefail
repo="$(cd "$(dirname "$0")/../.." && pwd)"
script="$repo/infra/local/restore-snapshot.sh"
test_root=$(mktemp -d /tmp/marketry-local-restore-test.XXXXXX)
trap 'rm -rf "$test_root"' EXIT
mkdir -p "$test_root/bin" "$test_root/home"
export MOCK_ROOT="$test_root" HOME="$test_root/home"
export SNAPSHOT_FILE="$test_root/snapshot.dump"
export PATH="$test_root/bin:$PATH"
status=0
cat > "$test_root/bin/docker" <<'MOCK'
#!/bin/bash
set -euo pipefail
for name in DOCKER_HOST DOCKER_CONTEXT COMPOSE_FILE COMPOSE_PROJECT_NAME COMPOSE_PROFILES COMPOSE_ENV_FILES COMPOSE_PATH_SEPARATOR; do
  [ -z "${!name-}" ] || { echo "상속 override가 전달됨: $name" >&2; exit 88; }
done
printf '%s\n' "$*" >> "$MOCK_ROOT/trace"
[ "$1 $2" = '--context orbstack' ] || exit 88
shift 2
if [ "$1 $2 $3" = 'context inspect orbstack' ]; then
  printf '%s\n' "${MOCK_ENDPOINT:-unix://$HOME/.orbstack/run/docker.sock}"
  exit 0
fi
[ "$1 $2 $3" = 'compose --project-name marketry-local' ] || exit 88
shift 3
[ "$1" = --project-directory ] && [ "${2##*/}" = local ] || exit 88
local_dir="$2"
shift 2
[ "$1" = -f ] && [ "$2" = "$local_dir/compose.yml" ] || exit 88
shift 2
[ "$1" = --env-file ] && [ "$2" = "$local_dir/.env" ] || exit 88
shift 2
if [ "$1" = up ]; then [ "${MOCK_UP_FAIL:-0}" = 0 ]; exit; fi
[ "$1 $2 $3" = 'exec -T db' ] || exit 88
shift 3
case "$1" in
  pg_restore)
    cat > /dev/null
    if [ "${2-}" = --list ]; then [ "${MOCK_INVALID:-0}" = 0 ]; else [ "${MOCK_RESTORE_FAIL:-0}" = 0 ]; fi
    ;;
  psql)
    [[ "$*" = *'marketry_db'* ]] || exit 88
    ;;
  *) exit 88 ;;
esac
MOCK
chmod +x "$test_root/bin/docker"
run() {
  status=0
  /bin/bash "$script" > "$test_root/out" 2> "$test_root/err" || status=$?
}
new_case() {
  : > "$test_root/trace"
  printf 'mock archive\n' > "$SNAPSHOT_FILE"
  unset MOCK_ENDPOINT MOCK_UP_FAIL MOCK_INVALID MOCK_RESTORE_FAIL
}
no_drop() { ! grep -q 'DROP DATABASE' "$test_root/trace"; }

new_case
rm "$SNAPSHOT_FILE"
run
[ "$status" = 0 ] && [ ! -s "$test_root/trace" ]
! grep -q '^완료$' "$test_root/out"
echo 'PASS: 스냅샷 없음은 건너뛰기로 표시하고 DB 작업 0회'

new_case
export MOCK_ENDPOINT=unix:///Users/deploy/.colima/default/docker.sock
run
[ "$status" != 0 ] && no_drop
! grep -q 'compose ' "$test_root/trace"
echo 'PASS: context가 운영 소켓을 가리키면 데몬 접속·삭제 전에 차단'

new_case
export DOCKER_HOST=unix:///wrong DOCKER_CONTEXT=wrong COMPOSE_FILE=wrong
export COMPOSE_PROJECT_NAME=production COMPOSE_PROFILES=wrong COMPOSE_ENV_FILES=wrong COMPOSE_PATH_SEPARATOR=wrong
run
[ "$status" = 0 ] || { cat "$test_root/err"; exit 1; }
grep -q 'DROP DATABASE IF EXISTS marketry_db' "$test_root/trace"
grep -q -- '--single-transaction' "$test_root/trace"
grep -q '^완료$' "$test_root/out"
unset DOCKER_HOST DOCKER_CONTEXT COMPOSE_FILE COMPOSE_PROJECT_NAME COMPOSE_PROFILES COMPOSE_ENV_FILES COMPOSE_PATH_SEPARATOR
echo 'PASS: 상속 override 제거·개발 context/파일/프로젝트 고정·트랜잭션 복원'

new_case
export MOCK_UP_FAIL=1
run
[ "$status" != 0 ] && no_drop
echo 'PASS: 개발 DB 기동 실패 시 삭제하지 않음'

new_case
export MOCK_INVALID=1
run
[ "$status" != 0 ] && no_drop
echo 'PASS: 손상된 덤프는 기존 개발 DB 삭제 전에 거부'

new_case
export MOCK_RESTORE_FAIL=1
run
[ "$status" != 0 ]
! grep -q '^완료$' "$test_root/out"
echo 'PASS: 복원 실패를 성공으로 기록하지 않음'
echo '6개 개발 스냅샷 복원 회귀 검사 통과'
