#!/bin/bash
# 배포 게이트. GitHub Actions가 ssh로 들어오면 deploy 계정의 authorized_keys 강제 명령(command=)이 이 스크립트를 실행한다.
# 레포 밖(~/Optional/bin/marketry-gate)에 설치해서 쓴다. 레포의 배포 절차가 바뀌어도 배포 직전 DB 덤프는 남게 하려는 구조다.
# 이 파일은 설치본의 원본이다. 고치면 다시 설치해야 반영된다.
#
# 입력은 SSH_ORIGINAL_COMMAND 하나뿐이다: deploy <target> <tag>
# 조회 전용 명령 gate-version, diagnostics도 고정 형식으로 허용한다.
#   target: application | nginx | renderer | all
#   tag:    ^[A-Za-z0-9._-]{1,128}$
# 레지스트리 포인터(:deployed, :previous) 갱신과 원복은 워크플로 쪽 일이라 여기서 하지 않는다.
set -euo pipefail

export PATH="$HOME/Optional/bin:$HOME/Optional/lima/bin:/usr/bin:/bin:/usr/sbin:/sbin"
export DOCKER_HOST="unix:///Users/deploy/.colima/default/docker.sock"

REPO_DIR="$HOME/Projects/marketry/marketry-backend"
COMPOSE_FILE="$REPO_DIR/infra/prod/compose.yml"
ENV_FILE="${MARKETRY_ENV_FILE:-$HOME/Projects/marketry/env/marketry.env}"
export MARKETRY_ENV_FILE="$ENV_FILE"
BACKUP_DIR="$HOME/Projects/marketry/backups/predeploy"
KEEP_DUMPS=5
LOCK_DIR="$HOME/.marketry-gate.lock"
IMAGE_STATE_DIR="$HOME/.marketry-gate-images"

HEALTH_TIMEOUT=600
HEALTH_INTERVAL=5
MAX_RESTART_COUNT=3

TARGET=""
TAG=""
SERVICES=()
CONTAINER_SNAPSHOT=""
PREDEPLOY_SNAPSHOT=""
CLEANUP_PLAN=""
RENAME_PENDING="$HOME/.marketry-application-rename"

log() {
  echo "=== [gate] $* ==="
}

reject() {
  echo "gate: 거부됨: $1" >&2
  exit 2
}

# SSH_ORIGINAL_COMMAND 문자열을 검증하고 TARGET, TAG에 담는다. 문자열을 셸로 평가하지 않는다.
# 줄바꿈 등이 섞인 입력도 전체 일치 정규식에서 걸러진다.
parse_command() {
  local input="${1-}"
  local re='^deploy (application|nginx|renderer|all) ([A-Za-z0-9._-]{1,128})$'

  if [[ ! "$input" =~ $re ]]; then
    reject "허용되지 않는 명령 형식입니다. 형식: deploy <application|nginx|renderer|all> <tag>"
  fi
  TARGET="${BASH_REMATCH[1]}"
  TAG="${BASH_REMATCH[2]}"
  case "$TARGET" in
    application) SERVICES=(marketry-application) ;;
    nginx) SERVICES=(marketry-nginx) ;;
    renderer) SERVICES=(marketry-renderer) ;;
    all) SERVICES=(marketry-application marketry-nginx marketry-renderer) ;;
  esac
}

acquire_lock() {
  if mkdir "$LOCK_DIR" 2>/dev/null; then
    echo "$$" >"$LOCK_DIR/pid"
    trap 'rm -rf "$LOCK_DIR"' EXIT
    return
  fi

  # 이전 실행이 강제 종료되어 잠금만 남은 경우에만 인수한다
  local holder=""
  [ -r "$LOCK_DIR/pid" ] && holder=$(cat "$LOCK_DIR/pid")
  if [ -n "$holder" ] && kill -0 "$holder" 2>/dev/null; then
    echo "gate: 다른 배포가 진행 중입니다(pid $holder)." >&2
    exit 3
  fi
  log "남아 있던 잠금을 인수합니다"
  rm -rf "$LOCK_DIR"
  mkdir "$LOCK_DIR"
  echo "$$" >"$LOCK_DIR/pid"
  trap 'rm -rf "$LOCK_DIR"' EXIT
}

predeploy_dump() {
  mkdir -p "$BACKUP_DIR"
  chmod 700 "$BACKUP_DIR"

  local stamp final tmp
  stamp=$(date +%Y%m%d-%H%M%S)
  final="$BACKUP_DIR/predeploy-$stamp.dump"
  tmp="$final.tmp"

  log "배포 직전 DB 덤프: $final"
  if ! docker exec marketry-postgres pg_dump -U marketry -Fc marketry_db >"$tmp"; then
    rm -f "$tmp"
    echo "gate: DB 덤프에 실패해 배포를 중단합니다." >&2
    exit 1
  fi
  if [ ! -s "$tmp" ]; then
    rm -f "$tmp"
    echo "gate: DB 덤프가 비어 있어 배포를 중단합니다." >&2
    exit 1
  fi
  mv "$tmp" "$final"

  # 파일명에 시각이 들어 있어 글롭 정렬이 곧 오래된 순서다
  local dumps=("$BACKUP_DIR"/predeploy-*.dump)
  local count=${#dumps[@]}
  local i
  if [ "$count" -gt "$KEEP_DUMPS" ]; then
    for ((i = 0; i < count - KEEP_DUMPS; i++)); do
      rm -f "${dumps[$i]}"
    done
  fi
}

update_repo() {
  log "레포 갱신(origin/main)"
  /usr/bin/git -C "$REPO_DIR" fetch origin main
  /usr/bin/git -C "$REPO_DIR" reset --hard origin/main
}

compose() {
  docker compose -f "$COMPOSE_FILE" --env-file "$ENV_FILE" "$@"
}

deploy_services() {
  case "$TARGET" in
    application | all) export APP_TAG="$TAG" ;;
  esac
  case "$TARGET" in
    nginx) export NGINX_TAG="$TAG" ;;
    all) export NGINX_TAG=latest ;;
  esac
  case "$TARGET" in
    renderer) export RENDERER_TAG="$TAG" ;;
    all) export RENDERER_TAG=latest ;;
  esac

  local legacy
  legacy=$(docker ps -aq --filter 'name=^/marketry-app$')
  if [ -e "$RENAME_PENDING" ] && [ "$TARGET" != all ] && [ "$TARGET" != application ]; then
    echo 'gate: 리네임 복구 중에는 application 또는 all만 허용합니다.' >&2
    return 1
  fi
  if [ -n "$legacy" ] && [ ! -e "$RENAME_PENDING" ] && [ "$TARGET" != all ]; then
    echo 'gate: 최초 앱 이름 변경은 target=all로 실행해야 합니다.' >&2
    return 1
  fi
  log "이미지 받기: ${SERVICES[*]} (app: ${APP_TAG:-deployed}, nginx: ${NGINX_TAG:-latest}, renderer: ${RENDERER_TAG:-latest})"
  compose pull "${SERVICES[@]}"
  if [ -e "$RENAME_PENDING" ]; then
    compose pull marketry-nginx
  fi
  if [ -n "$legacy" ]; then
    touch "$RENAME_PENDING"
    docker update --restart=no marketry-app
    docker stop --timeout 60 marketry-app
  fi

  log "기동: ${SERVICES[*]}"
  compose up -d --no-deps "${SERVICES[@]}"
  if [ -e "$RENAME_PENDING" ]; then
    compose up -d --no-deps --force-recreate marketry-nginx
  fi
}

image_repository() {
  case "$1" in
    marketry-application) echo ghcr.io/fwangchanju/marketry-application ;;
    marketry-nginx) echo ghcr.io/fwangchanju/marketry-nginx ;;
    marketry-renderer) echo ghcr.io/fwangchanju/marketry-renderer ;;
    *) return 1 ;;
  esac
}

valid_image_id() {
  local re='^sha256:[a-f0-9]{64}$'
  [[ "$1" =~ $re ]]
}

validate_image_ids() {
  local id
  for id in $1; do
    valid_image_id "$id" || return 1
  done
}

# 상태는 실행하지 않고 데이터로 읽는다. current/previous는 성공한 배포 상태다.
read_image_state() {
  STATE_CURRENT=-
  STATE_PREVIOUS=-
  STATE_IDS=""
  [ -e "$1" ] || return 0
  local kind id extra seen_current=0 seen_previous=0
  while read -r kind id extra; do
    [ -z "$extra" ] || return 1
    if [ "$id" != - ]; then
      valid_image_id "$id" || return 1
    fi
    case "$kind" in
      current) [ "$seen_current" -eq 0 ] || return 1; STATE_CURRENT="$id"; seen_current=1 ;;
      previous) [ "$seen_previous" -eq 0 ] || return 1; STATE_PREVIOUS="$id"; seen_previous=1 ;;
      image) valid_image_id "$id" || return 1; STATE_IDS="$STATE_IDS $id" ;;
      *) return 1 ;;
    esac
  done < "$1"
  [ "$seen_current" -eq 1 ] && [ "$seen_previous" -eq 1 ]
}

write_image_state() {
  local file="$1" tmp id
  tmp=$(mktemp "$file.tmp.XXXXXX") || return 1
  if ! {
    printf 'current %s\nprevious %s\n' "$STATE_CURRENT" "$STATE_PREVIOUS"
    for id in $STATE_IDS; do printf 'image %s\n' "$id"; done
  } > "$tmp"; then
    rm -f "$tmp"
    return 1
  fi
  if ! chmod 600 "$tmp" || ! mv "$tmp" "$file"; then
    rm -f "$tmp"
    return 1
  fi
}

snapshot_container_images() {
  local containers name id extra
  containers=$(docker ps -aq) || return 1
  CONTAINER_SNAPSHOT=""
  if [ -n "$containers" ]; then
    CONTAINER_SNAPSHOT=$(docker inspect --format '{{.Name}} {{.Image}}' $containers) || return 1
    while read -r name id extra; do
      [ -n "$name" ] && [ -z "$extra" ] && valid_image_id "$id" || return 1
    done <<< "$CONTAINER_SNAPSHOT"
  fi
}

container_image() {
  local name id
  while read -r name id; do
    if [ "$name" = "/$1" ]; then printf '%s\n' "$id"; return; fi
  done <<< "$CONTAINER_SNAPSHOT"
  echo -
}

remember_images() {
  local repository="$1" listed
  listed=$(docker image ls --quiet --no-trunc "$repository") || return 1
  validate_image_ids "$listed" || return 1
  STATE_IDS=$(printf '%s\n' $STATE_IDS $listed | awk 'NF' | sort -u) || return 1
}

# latest가 이동하기 전에 소유권을 확인한 ID만 기록한다. 조회 실패 시 정리를 건너뛴다.
prepare_image_cleanup() {
  mkdir -p "$IMAGE_STATE_DIR" && chmod 700 "$IMAGE_STATE_DIR" || return 1
  snapshot_container_images || return 1
  PREDEPLOY_SNAPSHOT="$CONTAINER_SNAPSHOT"
  local service repository file
  for service in "${SERVICES[@]}"; do
    repository=$(image_repository "$service") || return 1
    file="$IMAGE_STATE_DIR/$service"
    read_image_state "$file" || return 1
    if [ ! -e "$file" ]; then
      STATE_CURRENT=$(container_image "$service") || return 1
    fi
    remember_images "$repository" && write_image_state "$file" || return 1
  done
}

# 모든 대상의 보호 정보를 확인한 뒤 삭제한다. 중지 컨테이너도 보호한다.
build_image_cleanup_plan() {
  snapshot_container_images || return 1
  local available service repository file current id metadata actual created tags digests extra
  local records live_ids foreign_ids reference timestamp fraction latest_ids protected_ids
  available=$(docker image ls --quiet --no-trunc) || return 1
  validate_image_ids "$available" || return 1
  available=$(printf '%s\n' "$available" | tr '\n' ' ') || return 1
  CLEANUP_PLAN=""
  for service in "${SERVICES[@]}"; do
    repository=$(image_repository "$service") || return 1
    file="$IMAGE_STATE_DIR/$service"
    read_image_state "$file" || return 1
    current=$(container_image "$service") || return 1
    valid_image_id "$current" || return 1
    if [ "$current" != "$STATE_CURRENT" ]; then
      STATE_PREVIOUS="$STATE_CURRENT"
      STATE_CURRENT="$current"
    fi
    write_image_state "$file" && remember_images "$repository" || return 1
    records=""; live_ids=""; foreign_ids=""
    for id in $STATE_IDS; do
      case " $available " in *" $id "*) ;; *) continue ;; esac
      metadata=$(docker image inspect --format '{{.Id}}|{{.Created}}|{{range .RepoTags}}{{.}} {{end}}|{{range .RepoDigests}}{{.}} {{end}}' "$id") || return 1
      IFS='|' read -r actual created tags digests extra <<< "$metadata"
      [ "$actual" = "$id" ] && [ -z "$extra" ] || return 1
      local date_re='^[0-9]{4}-[0-9]{2}-[0-9]{2}T[0-9]{2}:[0-9]{2}:[0-9]{2}(\.[0-9]{1,9})?Z$'
      [[ "$created" =~ $date_re ]] || return 1
      timestamp="${created%Z}"
      fraction=0
      if [[ "$timestamp" == *.* ]]; then fraction="${timestamp#*.}"; timestamp="${timestamp%%.*}"; fi
      fraction="${fraction}000000000"
      records="$records$timestamp.${fraction:0:9} $id"$'\n'
      live_ids="$live_ids $id"
      for reference in $tags $digests; do
        case "$reference" in "$repository":* | "$repository"@*) ;; *) foreign_ids="$foreign_ids $id" ;; esac
      done
    done
    STATE_IDS="$live_ids"
    write_image_state "$file" || return 1
    latest_ids=$(printf '%s' "$records" | sort -r | awk 'NR <= 2 {print $2}') || return 1
    protected_ids="$STATE_CURRENT $STATE_PREVIOUS $foreign_ids $latest_ids"
    protected_ids="$protected_ids $(printf '%s\n%s\n' "$CONTAINER_SNAPSHOT" "$PREDEPLOY_SNAPSHOT" | awk '{print $2}')"
    protected_ids=$(printf '%s\n' "$protected_ids" | tr '\n' ' ') || return 1
    for id in $STATE_IDS; do
      case " $protected_ids " in
        *" $id "*) ;;
        *) CLEANUP_PLAN="$CLEANUP_PLAN$service $id"$'\n' ;;
      esac
    done
  done
}

cleanup_images() {
  local service id
  while read -r service id; do
    [ -n "$id" ] || continue
    if ! docker image rm "$id"; then
      log "경고: $service 오래된 이미지를 삭제하지 못해 보존합니다 ($id)"
    fi
  done <<< "$CLEANUP_PLAN"
}

# 컨테이너가 크래시 루프에 빠졌는지 본다. 빠졌으면 1을 반환한다
crash_looping() {
  local count baseline="${2:-0}"
  count=$(docker inspect --format='{{.RestartCount}}' "$1")
  if ! [[ "$count" =~ ^[0-9]+$ && "$baseline" =~ ^[0-9]+$ ]]; then
    echo "gate: $1 재시작 횟수를 확인하지 못했습니다." >&2
    return 0
  fi
  if [ "$((count - baseline))" -ge "$MAX_RESTART_COUNT" ]; then
    echo "gate: $1 검사 구간 재시작 $((count - baseline))회, 크래시 루프로 판단합니다." >&2
    return 0
  fi
  return 1
}

# 앱 헬스 응답 본문을 가져온다. 앱 이미지의 도구(curl, wget)에 기대지 않는다.
# nginx 컨테이너(busybox wget)가 떠 있으면 같은 네트워크에서 요청하고, 아니면(첫 기동 등)
# 앱 컨테이너 안에서 bash /dev/tcp로 직접 요청한다.
app_health_body() {
  if [ "$(docker inspect --format='{{.State.Running}}' marketry-nginx 2>/dev/null || true)" = "true" ]; then
    docker exec marketry-nginx wget -qO- -T 5 http://marketry-application:8081/actuator/health
  else
    docker exec marketry-application bash -c \
      'exec 3<>/dev/tcp/127.0.0.1/8081 && printf "GET /actuator/health HTTP/1.0\r\nHost: localhost\r\n\r\n" >&3 && cat <&3'
  fi
}

app_healthy() {
  local body
  body=$(app_health_body 2>/dev/null) || return 1
  [[ "$body" == *'"status":"UP"'* ]]
}

# 헬스 조건(함수 또는 명령)이 성공할 때까지 기다린다. 크래시 루프는 container 기준으로 확인한다. 실패하면 1로 끝낸다
wait_healthy() {
  local container="$1"
  shift
  local elapsed=0

  while [ "$elapsed" -lt "$HEALTH_TIMEOUT" ]; do
    if crash_looping "$container"; then
      exit 1
    fi
    if "$@" >/dev/null 2>&1; then
      log "$container 헬스체크 통과"
      return
    fi
    sleep "$HEALTH_INTERVAL"
    elapsed=$((elapsed + HEALTH_INTERVAL))
  done

  echo "gate: $container 이(가) ${HEALTH_TIMEOUT}초 안에 정상 기동하지 않았습니다." >&2
  docker logs --tail 50 "$container" >&2 || true
  exit 1
}

# nginx는 헬스 엔드포인트가 없어 잠시 지켜보며 크래시 루프만 확인한다
watch_container() {
  local container="$1"
  local elapsed=0 baseline
  # 재사용한 nginx의 과거 재시작을 이번 배포 실패로 취급하지 않는다.
  baseline=$(docker inspect --format='{{.RestartCount}}' "$container")
  [[ "$baseline" =~ ^[0-9]+$ ]] || { echo "gate: $container 재시작 기준 조회 실패" >&2; exit 1; }

  while [ "$elapsed" -lt 15 ]; do
    if [ "$(docker inspect --format='{{.State.Running}}' "$container")" != "true" ] || crash_looping "$container" "$baseline"; then
      echo "gate: $container 이(가) 정상 실행 중이 아닙니다." >&2
      docker logs --tail 50 "$container" >&2 || true
      exit 1
    fi
    sleep 3
    elapsed=$((elapsed + 3))
  done
  log "$container 크래시 루프 미감지"
}

health_check() {
  case "$TARGET" in
    application | all)
      wait_healthy marketry-application app_healthy
      ;;
  esac
  case "$TARGET" in
    renderer | all)
      wait_healthy marketry-renderer docker exec marketry-renderer node -e "require('http').get('http://localhost:3000/health',r=>process.exit(r.statusCode===200?0:1)).on('error',()=>process.exit(1))"
      ;;
  esac
  case "$TARGET" in
    nginx | all)
      watch_container marketry-nginx
      ;;
  esac
}

nginx_upstream_healthy() {
  local body
  body=$(docker exec marketry-nginx wget -qO- -T 5 http://127.0.0.1/internal/upstream-health) || return 1
  [[ "$body" == *'"status":"UP"'* ]]
}

# 출력 범위를 고정한 조회만 허용한다. 환경 파일이나 전체 환경은 출력하지 않는다.
diagnostics() {
  app_healthy
  nginx_upstream_healthy
  docker inspect --format '{{.Name}} running={{.State.Running}} restarts={{.RestartCount}}' \
    marketry-application marketry-nginx marketry-renderer marketry-cloudflared marketry-postgres
  docker exec marketry-application printenv SPRING_PROFILES_ACTIVE
  docker logs --since 24h marketry-application 2>&1 | \
    grep -E '스냅샷 정리|종목 정보 동기화 (시작|완료)|휴장일|Started MarketryApplication|profiles? (is|are) active' | tail -n 30 || true
  /bin/launchctl print system/kr.co.marketry.backup | \
    grep -E 'state =|runs =|last exit code =|"Hour"|"Minute"'
  echo 'PASS: 앱·nginx upstream 응답 및 고정 범위 진단 완료'
}

main() {
  # 입력 검증만 시험하는 모드. 강제 명령으로는 인자를 넘길 수 없어서 로컬에서만 쓸 수 있다
  if [ "${1-}" = "--check" ]; then
    parse_command "${2-}"
    echo "ok target=$TARGET tag=$TAG"
    exit 0
  fi

  case "${SSH_ORIGINAL_COMMAND-}" in
    gate-version) echo 'marketry-gate/v2'; return ;;
    diagnostics) diagnostics; return ;;
  esac
  parse_command "${SSH_ORIGINAL_COMMAND-}"
  log "배포 요청: target=$TARGET tag=$TAG"

  acquire_lock
  predeploy_dump
  local cleanup_ready=false
  if prepare_image_cleanup; then
    cleanup_ready=true
  else
    log "경고: 이미지 정리 준비에 실패해 이번 정리를 건너뜁니다"
  fi
  update_repo
  deploy_services
  health_check
  if [ -e "$RENAME_PENDING" ]; then
    wait_healthy marketry-nginx nginx_upstream_healthy
    # 앱 원복에서도 nginx가 새 앱 주소로 연결되는 것까지 확인한 뒤 정리한다.
    if [ -n "$(docker ps -aq --filter 'name=^/marketry-app$')" ]; then
      docker rm marketry-app
    fi
    rm -f "$RENAME_PENDING"
  fi
  if [ "$cleanup_ready" = true ]; then
    if build_image_cleanup_plan; then
      cleanup_images
    else
      log "경고: 이미지 보호 정보 확인에 실패해 이번 정리를 건너뜁니다"
    fi
  fi
  log "배포 완료"
}

main "$@"
