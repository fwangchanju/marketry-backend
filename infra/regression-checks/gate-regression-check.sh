#!/bin/bash
# 실제 Docker·Git·환경 파일을 사용하지 않는 macOS Bash 3.2 회귀 검사.
set -euo pipefail

gate_source="$(cd "$(dirname "$0")/../prod" && pwd)/gate.sh"
test_root=$(mktemp -d /tmp/marketry-gate-test.XXXXXX)
trap 'rm -rf "$test_root"' EXIT

# 운영 PATH와 Docker 주소는 로드하지 않는다. docker는 아래 모조 함수만 호출한다.
sed '/^export PATH=/d; /^export DOCKER_HOST=/d; /^main "\$@"$/d' "$gate_source" > "$test_root/gate-functions.sh"
source "$test_root/gate-functions.sh"

case_dir=""
RUN_STATUS=0
test_count=0

image_id() { printf 'sha256:%064x' "$1"; }

new_case() {
  case_dir=$(mktemp -d "$test_root/case.XXXXXX")
  mkdir -p "$case_dir/images" "$case_dir/containers" "$case_dir/registry" "$case_dir/fail"
  IMAGE_STATE_DIR="$case_dir/state"
  BACKUP_DIR="$case_dir/backups"
  LOCK_DIR="$case_dir/lock"
  RENAME_PENDING="$case_dir/rename-pending"
  ENV_FILE="$case_dir/unused-environment"
  COMPOSE_FILE="$case_dir/unused-compose"
  : > "$case_dir/trace"
}

add_image() {
  local id dir repository
  id=$(image_id "$1"); dir="$case_dir/images/$id"
  repository=$(image_repository "${3:-marketry-application}")
  mkdir -p "$dir"
  printf '2026-01-%02dT00:00:00Z\n' "$2" > "$dir/created"
  printf '%s:%s\n' "$repository" "${4:-sha-$1}" > "$dir/tags"
  : > "$dir/digests"
}

incoming() {
  add_image "$1" "$2" "${3:-marketry-application}"
  : > "$case_dir/images/$(image_id "$1")/tags"
  image_id "$1" > "$case_dir/registry/${3:-marketry-application}.${4:-main}"
}

set_container() { image_id "$2" > "$case_dir/containers/$1"; }

ledger() {
  mkdir -p "$IMAGE_STATE_DIR"
  local current="$1" previous="$2" id
  shift 2
  {
    printf 'current %s\nprevious %s\n' "$(image_id "$current")" "$(image_id "$previous")"
    for id in "$@"; do printf 'image %s\n' "$(image_id "$id")"; done
  } > "$IMAGE_STATE_DIR/marketry-application"
}

mock_failure() { [ -e "$case_dir/fail/$1" ]; }

docker() {
  printf '%s\n' "$*" >> "$case_dir/trace"
  local command="$1" dir id repository file service action tag reference
  shift
  case "$command" in
    ps)
      if [ "$*" = '-aq --filter name=^/marketry-app$' ]; then
        [ ! -f "$case_dir/containers/marketry-app" ] || echo legacy-app
        return 0
      fi
      [ "$*" = -aq ] || return 88
      mock_failure ps && return 1
      for file in "$case_dir"/containers/*; do [ -f "$file" ] && basename "$file"; done
      return 0
      ;;
    inspect)
      [ "$1" = --format ] && [ "$2" = '{{.Name}} {{.Image}}' ] || return 88
      mock_failure container-inspect && return 1
      shift 2
      for service in "$@"; do
        printf '/%s %s\n' "$service" "$(cat "$case_dir/containers/$service")"
      done
      ;;
    image)
      action="$1"; shift
      case "$action" in
        ls)
          [ "$1" = --quiet ] && [ "$2" = --no-trunc ] || return 88
          shift 2
          mock_failure image-list && return 1
          repository="${1:-}"
          for dir in "$case_dir"/images/*; do
            [ -d "$dir" ] || continue
            if [ -z "$repository" ] || grep -Fq "$repository:" "$dir/tags"; then basename "$dir"; fi
          done
          return 0
          ;;
        inspect)
          [ "$1" = --format ] || return 88
          id="$3"; dir="$case_dir/images/$id"
          mock_failure image-inspect && return 1
          [ -d "$dir" ] || return 1
          printf '%s|%s|%s|%s\n' "$id" "$(cat "$dir/created")" \
            "$(tr '\n' ' ' < "$dir/tags")" "$(tr '\n' ' ' < "$dir/digests")"
          ;;
        rm)
          [ "$#" -eq 1 ] && valid_image_id "$1" || return 88
          id="$1"
          mock_failure remove && return 1
          for file in "$case_dir"/containers/*; do
            [ -f "$file" ] || continue
            [ "$(cat "$file")" != "$id" ] || return 1
          done
          # Docker가 여러 태그의 ID 삭제를 거절하는 동작도 재현한다.
          [ "$(wc -l < "$case_dir/images/$id/tags")" -le 1 ] || return 1
          rm -rf "$case_dir/images/$id"
          ;;
        *) return 88 ;;
      esac
      ;;
    exec)
      [ "$*" = 'marketry-postgres pg_dump -U marketry -Fc marketry_db' ] || return 88
      mock_failure backup && return 1
      printf 'mock database dump\n'
      ;;
    update|stop|rm)
      mock_failure "$command" && return 1
      if [ "$command" = rm ]; then rm -f "$case_dir/containers/marketry-app"; fi
      return 0
      ;;
    compose)
      while [ "$#" -gt 0 ] && [ "$1" != pull ] && [ "$1" != up ]; do shift; done
      [ "$#" -gt 0 ] || return 88
      action="$1"; shift
      mock_failure "$action" && return 1
      printf 'tags %s %s %s\n' "${APP_TAG:-deployed}" "${NGINX_TAG:-latest}" "${RENDERER_TAG:-latest}" >> "$case_dir/trace"
      for service in "$@"; do
        case "$service" in -d | --no-deps | --force-recreate) continue ;; esac
        repository=$(image_repository "$service") || return 88
        case "$service" in
          marketry-application) tag="${APP_TAG:-deployed}" ;;
          marketry-nginx) tag="${NGINX_TAG:-latest}" ;;
          marketry-renderer) tag="${RENDERER_TAG:-latest}" ;;
        esac
        reference="$repository:$tag"
        file="$case_dir/registry/$service.$tag"
        [ -f "$file" ] || return 1
        id=$(cat "$file")
        if [ "$action" = pull ]; then
          for dir in "$case_dir"/images/*; do
            [ -d "$dir" ] || continue
            grep -Fvx "$reference" "$dir/tags" > "$dir/tags.tmp" || true
            mv "$dir/tags.tmp" "$dir/tags"
          done
          printf '%s\n' "$reference" >> "$case_dir/images/$id/tags"
        else
          printf '%s\n' "$id" > "$case_dir/containers/$service"
        fi
      done
      ;;
    *) echo "예상하지 않은 모조 명령: $command $*" >&2; return 88 ;;
  esac
}

update_repo() { :; }
wait_healthy() {
  printf 'health %s\n' "$1" >> "$case_dir/trace"
  if mock_failure health || mock_failure "health-$1"; then return 1; fi
}
watch_container() { wait_healthy "$1"; }

run_gate() {
  set +e
  (
    set -e
    SSH_ORIGINAL_COMMAND="$1"
    if mock_failure write-state; then write_image_state() { return 1; }; fi
    main
  ) > "$case_dir/result" 2>&1
  RUN_STATUS=$?
  set -e
}

success() {
  if [ "$RUN_STATUS" -ne 0 ]; then cat "$case_dir/result"; exit 1; fi
}
exists() { [ -d "$case_dir/images/$(image_id "$1")" ]; }
absent() { ! exists "$1"; }
no_removal() { ! grep -q '^image rm ' "$case_dir/trace"; }
passed() { test_count=$((test_count + 1)); printf 'PASS: %s\n' "$1"; }

new_case
incoming 1 1 marketry-application main
incoming 2 2 marketry-nginx latest
incoming 3 3 marketry-renderer latest
run_gate 'deploy all main'; success
grep -q '^tags main latest latest$' "$case_dir/trace"
passed '전체 배포의 앱 태그와 latest 분리'

for service in nginx renderer; do
  new_case
  incoming 1 1 "marketry-$service" release-123
  run_gate "deploy $service release-123"; success
  [ "$(cat "$case_dir/containers/marketry-$service")" = "$(image_id 1)" ]
done
passed '개별 서비스 요청 태그 유지'

new_case
add_image 1 1 marketry-application main; set_container marketry-application 1
add_image 2 2; add_image 3 3; add_image 4 9
printf '%s:alias\n' "$(image_repository marketry-application)" >> "$case_dir/images/$(image_id 4)/tags"
add_image 6 1 marketry-renderer
ledger 1 2 1 2 3 4
incoming 5 10
run_gate 'deploy application main'; success
exists 1; exists 4; exists 5; exists 6; absent 2; absent 3
passed '실행·직전 성공·최신 고유 이미지 2개와 다른 서비스 보호'

new_case
add_image 1 1 marketry-renderer latest; set_container marketry-renderer 1
incoming 2 2 marketry-renderer latest
run_gate 'deploy renderer latest'; success
[ ! -s "$case_dir/images/$(image_id 1)/tags" ]
incoming 3 3 marketry-renderer latest
run_gate 'deploy renderer latest'; success
absent 1; exists 2; exists 3
passed 'latest 이동 뒤 태그 없는 추적 이미지 정리'

# nginx 설정 파일만 바뀌고 이미지는 같아도 컨테이너를 새로 만들어야 새 설정이 연결된다(2026-11-11).
new_case
add_image 1 1 marketry-nginx latest; set_container marketry-nginx 1
incoming 1 1 marketry-nginx latest
run_gate 'deploy nginx latest'; success
grep -q -- '--force-recreate marketry-nginx' "$case_dir/trace"
[ "$(grep -c ' up ' "$case_dir/trace")" = 1 ]
passed '이미지가 같아도 nginx 배포는 컨테이너를 한 번만 새로 만든다'

new_case
add_image 1 1; add_image 2 2; add_image 3 3; add_image 4 4
printf 'other.example/service:old\n' >> "$case_dir/images/$(image_id 1)/tags"
printf 'other.example/service@sha256:abc\n' >> "$case_dir/images/$(image_id 2)/digests"
set_container stopped-sidecar 3
: > "$case_dir/images/$(image_id 4)/tags"
add_image 10 10; incoming 11 11
run_gate 'deploy application main'; success
exists 1; exists 2; exists 3; exists 4
passed '외부 태그·digest·중지 컨테이너·추적하지 않은 이미지 보호'

new_case
add_image 1 1 marketry-application main; add_image 2 2; add_image 9 9
set_container marketry-application 1; ledger 1 2 1 2
incoming 1 1
run_gate 'deploy application main'; success
grep -q "^previous $(image_id 2)$" "$IMAGE_STATE_DIR/marketry-application"
exists 2
passed '동일 성공 이미지 재배포의 previous 유지'

new_case
add_image 2 2 marketry-application main; add_image 1 1
set_container marketry-application 2; ledger 2 1 1 2
incoming 9 9; touch "$case_dir/fail/health"
run_gate 'deploy application main'
[ "$RUN_STATUS" -ne 0 ]; no_removal
rm "$case_dir/fail/health"; incoming 2 2 marketry-application deployed
run_gate 'deploy application deployed'; success
grep -q "^current $(image_id 2)$" "$IMAGE_STATE_DIR/marketry-application"
grep -q "^previous $(image_id 1)$" "$IMAGE_STATE_DIR/marketry-application"
exists 1
passed '배포 실패 후 복구의 직전 성공 이미지 보호'

for failure in backup pull up health; do
  new_case
  add_image 1 1; incoming 10 10; touch "$case_dir/fail/$failure"
  run_gate 'deploy application main'
  [ "$RUN_STATUS" -ne 0 ]; no_removal
done
passed '백업·pull·기동·헬스 실패 시 삭제 없음'

new_case
add_image 1 1
incoming 10 10 marketry-application main
incoming 11 11 marketry-nginx latest
incoming 12 12 marketry-renderer latest
touch "$case_dir/fail/health-marketry-renderer"
run_gate 'deploy all main'
[ "$RUN_STATUS" -ne 0 ]; no_removal
grep -q '^health marketry-application$' "$case_dir/trace"
grep -q '^health marketry-renderer$' "$case_dir/trace"
passed '전체 배포에서 렌더러 헬스 실패 시 모든 정리 생략'

for failure in ps container-inspect image-list image-inspect write-state; do
  new_case
  add_image 1 1; set_container marketry-application 1; incoming 10 10
  touch "$case_dir/fail/$failure"
  run_gate 'deploy application main'; success; no_removal
done
passed '조회·상태 기록 실패 시 정상 배포 유지와 삭제 생략'

new_case
add_image 1 1; add_image 9 9; incoming 10 10
touch "$case_dir/fail/remove"
run_gate 'deploy application main'; success
grep -q '^image rm ' "$case_dir/trace"; grep -q '경고' "$case_dir/result"; exists 1
passed '삭제 실패 시 경고와 이미지 보존'

new_case
add_image 1 1 marketry-application main; set_container marketry-application 1
add_image 3 4; add_image 4 4; add_image 5 4
printf '2026-01-04T00:00:00.1Z\n' > "$case_dir/images/$(image_id 3)/created"
printf '2026-01-04T00:00:00.01Z\n' > "$case_dir/images/$(image_id 4)/created"
printf '2026-01-04T00:00:00Z\n' > "$case_dir/images/$(image_id 5)/created"
ledger 1 1 1 3 4 5 40; incoming 2 2
run_gate 'deploy application main'; success
exists 3; exists 4; absent 5
passed '생성 시각 소수점 정렬과 사라진 추적 ID 제외'

new_case
add_image 1 1; incoming 10 10
mkdir -p "$IMAGE_STATE_DIR"
printf 'image $(touch must-not-exist)\n' > "$IMAGE_STATE_DIR/marketry-application"
run_gate 'deploy application main'; success; no_removal
[ ! -e must-not-exist ]
passed '손상된 상태를 실행하지 않고 정리 생략'

new_case
add_image 1 1; set_container marketry-app 1
incoming 2 2; incoming 3 3 marketry-nginx latest; incoming 4 4 marketry-renderer latest
run_gate 'deploy application main'; [ "$RUN_STATUS" -ne 0 ]
! grep -q '^stop ' "$case_dir/trace"
run_gate 'deploy all main'; success
[ ! -e "$case_dir/containers/marketry-app" ]; [ ! -e "$RENAME_PENDING" ]
/usr/bin/python3 - "$case_dir/trace" <<'PYTRACE'
import sys
lines=open(sys.argv[1]).read().splitlines()
def at(prefix): return next(i for i,line in enumerate(lines) if line.startswith(prefix))
pull=next(i for i,l in enumerate(lines) if ' pull ' in l)
stop=at('stop --timeout 60 marketry-app')
up=next(i for i,l in enumerate(lines) if ' up ' in l)
assert pull < stop < up < at('health marketry-nginx') < at('rm marketry-app')
assert any('--force-recreate marketry-nginx' in l for l in lines)
PYTRACE
passed '최초 리네임은 all만 허용하고 pull·기존 앱 중지·새 앱 및 nginx 검증 후 구 컨테이너 정리'

new_case
add_image 1 1; set_container marketry-app 1
incoming 2 2; incoming 3 3 marketry-nginx latest; incoming 4 4 marketry-renderer latest
incoming 1 1 marketry-application deployed
touch "$case_dir/fail/up"
run_gate 'deploy all main'; [ "$RUN_STATUS" -ne 0 ]
[ -e "$RENAME_PENDING" ]; [ -e "$case_dir/containers/marketry-app" ]
rm "$case_dir/fail/up"
touch "$case_dir/fail/health-marketry-nginx"
run_gate 'deploy application deployed'; [ "$RUN_STATUS" -ne 0 ]
[ -e "$RENAME_PENDING" ]; [ -e "$case_dir/containers/marketry-app" ]
rm "$case_dir/fail/health-marketry-nginx"
run_gate 'deploy application deployed'; success
[ ! -e "$RENAME_PENDING" ]; [ ! -e "$case_dir/containers/marketry-app" ]
passed '리네임 실패 원복에서도 nginx 재생성·upstream 검증이 실패하면 중단하고 재시도 상태 보존'

# 기존 배포 검사는 헬스 함수를 대체하므로 실제 nginx 감시 함수는 별도 subshell에서 검사한다.
(
  source "$test_root/gate-functions.sh"
  restart_count=3
  running=true
  increase=0
  docker() {
    case "$*" in
      *RestartCount*) printf '%s\n' "$restart_count" ;;
      *State.Running*) printf '%s\n' "$running" ;;
      logs*) : ;;
      *) return 1 ;;
    esac
  }
  sleep() { restart_count=$((restart_count + increase)); }
  watch_container marketry-nginx >/dev/null
  increase=1
  if (watch_container marketry-nginx) >/dev/null 2>&1; then
    echo 'FAIL: 검사 구간의 반복 재시작을 놓침' >&2; exit 1
  fi
  increase=0
  running=false
  if (watch_container marketry-nginx) >/dev/null 2>&1; then
    echo 'FAIL: 중지된 nginx를 정상으로 판정' >&2; exit 1
  fi
  running=true
  restart_count=invalid
  if (watch_container marketry-nginx) >/dev/null 2>&1; then
    echo 'FAIL: 잘못된 재시작 기준을 허용' >&2; exit 1
  fi
)
passed 'nginx 과거 재시작은 허용하고 검사 구간 반복 재시작·중지·조회 오류는 거부'

printf '%s개 게이트 회귀 검사 통과\n' "$test_count"
