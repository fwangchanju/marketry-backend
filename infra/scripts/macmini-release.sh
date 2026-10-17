#!/bin/bash
# GitHub Actions runner 전용. 운영 명령은 설치된 SSH 강제 명령 게이트로만 보낸다.
set -euo pipefail

IMAGE=ghcr.io/fwangchanju/market-monitor

fail() { echo "::error::$*" >&2; return 1; }
output() { printf '%s=%s\n' "$1" "$2" >> "${GITHUB_OUTPUT:?}"; }

route() {
  [ "${REF_NAME:-}" = refs/heads/main ] || { fail '수동·프론트 배포는 main에서만 실행합니다.'; return 1; }
  case "${EVENT_NAME:-}" in
    workflow_dispatch) SERVER="${REQUESTED_SERVER-}"; TARGET="${REQUESTED_TARGET:-application}" ;;
    repository_dispatch) SERVER=macmini; TARGET=nginx ;;
    *) fail '허용되지 않은 배포 이벤트입니다.'; return 1 ;;
  esac
  case "$SERVER" in oracle|macmini) ;; *) fail 'server는 oracle 또는 macmini여야 합니다.'; return 1 ;; esac
  case "$TARGET" in application|nginx|renderer|all|rollback|connection-check) ;; *) fail '허용되지 않은 target입니다.'; return 1 ;; esac
  if [ "$TARGET" = connection-check ]; then
    [ "$SERVER" = macmini ] || { fail 'connection-check는 macmini 전용입니다.'; return 1; }

  fi
  output server "$SERVER"
  output target "$TARGET"
}

ssh_setup() {
  : "${RUNNER_TEMP:?}" "${MACMINI_SSH_KEY:?}" "${MACMINI_HOST:?}" "${MACMINI_USER:?}"
  [ "$MACMINI_USER" = deploy ] || { fail '맥미니 SSH 사용자는 deploy여야 합니다.'; return 1; }
  [[ "$MACMINI_HOST" =~ ^[A-Za-z0-9][A-Za-z0-9.:-]*$ ]] || { fail '잘못된 맥미니 호스트 형식입니다.'; return 1; }
  mkdir -p "$RUNNER_TEMP/marketry-ssh"
  chmod 700 "$RUNNER_TEMP/marketry-ssh"
  (umask 077; printf '%s\n' "$MACMINI_SSH_KEY" > "$RUNNER_TEMP/marketry-ssh/key")
}

remote() {
  ssh -T -i "$RUNNER_TEMP/marketry-ssh/key" \
    -o BatchMode=yes -o IdentitiesOnly=yes -o ConnectTimeout=20 \
    -o ServerAliveInterval=15 -o ServerAliveCountMax=3 \
    -o StrictHostKeyChecking=accept-new \
    -o "UserKnownHostsFile=$RUNNER_TEMP/marketry-ssh/known_hosts" \
    "$MACMINI_USER@$MACMINI_HOST" "$1"
}

connection_check() {
  local response status=0
  response=$(remote connection-check 2>&1) || status=$?
  # parse_command가 부작용 전에 거부한 경우에만 통과. SSH 실패(255)나 임의 셸의 응답은 실패한다.
  if [ "$status" -ne 2 ] || ! printf '%s\n' "$response" | grep -Fxq 'gate: 거부됨: 허용되지 않는 명령 형식입니다. 형식: deploy <application|nginx|renderer|all> <tag>'; then
    fail '맥미니 접속·배포 게이트 검증에 실패했습니다. Tailscale/SSH/강제 명령 설정을 확인합니다.'
    return 1
  fi
  echo 'Tailscale·SSH·강제 명령 게이트 검증 성공. 배포와 DB 작업은 실행하지 않았습니다.'
}

# 선택 포인터라도 통신·권한 오류를 태그 부재로 취급하지 않는다.
inspect_digest() {
  local tag="$1" optional="${2:-false}" digest error_file
  error_file=$(mktemp "$RUNNER_TEMP/marketry-manifest.XXXXXX")
  if digest=$(docker buildx imagetools inspect --format '{{.Manifest.Digest}}' "$IMAGE:$tag" 2>"$error_file"); then
    rm -f "$error_file"
    [[ "$digest" =~ ^sha256:[a-f0-9]{64}$ ]] || { fail "잘못된 $tag 이미지 다이제스트입니다."; return 1; }
    printf '%s\n' "$digest"
  else
    if [ "$optional" = true ] && { grep -Fxq "ERROR: $IMAGE:$tag: not found" "$error_file" || grep -Eq '(^|: )manifest unknown(:|$)' "$error_file"; }; then
      rm -f "$error_file"
      return 0
    fi
    rm -f "$error_file"
    fail "$tag 이미지 조회에 실패했습니다. 인증·연결 또는 필수 이미지 존재 여부를 확인합니다."
    return 1
  fi
}

pin_digest() {
  local digest="$1" tag="deploy-${1#sha256:}"
  docker buildx imagetools create -t "$IMAGE:$tag" "$IMAGE@$digest" >&2 || return 1
  printf '%s\n' "$tag"
}

prepare_images() {
  local target="${TARGET:?}" new old previous tag revert_tag
  case "$target" in
    application|all|rollback)
      old=$(inspect_digest deployed true)
      if [ "$target" = rollback ]; then
        [ -n "$old" ] || { fail 'rollback에는 deployed 이미지가 필요합니다.'; return 1; }
        previous=$(inspect_digest previous)
        new="$previous"
      else
        new=$(inspect_digest main)
      fi
      tag=$(pin_digest "$new")
      output new_digest "$new"
      output old_digest "$old"
      output image_tag "$tag"
      if [ -n "$old" ]; then
        revert_tag=$(pin_digest "$old")
        output revert_tag "$revert_tag"
      fi
      ;;
    nginx|renderer) output image_tag latest ;;
    *) fail '이미지 준비 대상이 잘못됐습니다.'; return 1 ;;
  esac
}

deploy() {
  local target="${TARGET:?}" tag="${IMAGE_TAG:?}"
  case "$target" in application|nginx|renderer|all) ;; rollback) target=application ;; *) fail '배포 대상이 잘못됐습니다.'; return 1 ;; esac
  [[ "$tag" =~ ^[A-Za-z0-9._-]{1,128}$ ]] || { fail '배포 태그가 잘못됐습니다.'; return 1; }
  remote "deploy $target $tag"
}

update_pointers() {
  case "${TARGET:?}" in application|all|rollback) ;; *) return 0 ;; esac
  : "${NEW_DIGEST:?}"
  if [ "$NEW_DIGEST" = "${OLD_DIGEST:-}" ]; then
    echo '동일 이미지 재배포이므로 deployed/previous를 유지합니다.'
    return 0
  fi
  if [ -n "${OLD_DIGEST:-}" ]; then
    docker buildx imagetools create -t "$IMAGE:previous" "$IMAGE@$OLD_DIGEST"
  fi
  docker buildx imagetools create -t "$IMAGE:deployed" "$IMAGE@$NEW_DIGEST"
}

main() {
  case "${1-}" in
    route) route ;;
    ssh-setup) ssh_setup ;;
    connection-check) connection_check ;;
    prepare-images) prepare_images ;;
    deploy) deploy ;;
    update-pointers) update_pointers ;;
    ssh-cleanup) rm -rf "${RUNNER_TEMP:?}/marketry-ssh" ;;
    *) fail '허용되지 않은 runner 동작입니다.' ;;
  esac
}

main "$@"
