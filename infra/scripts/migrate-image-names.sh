#!/bin/bash
# Actions runner에서 레지스트리 이름만 이전한다. 서버·DB에는 접속하지 않는다.
set -euo pipefail

inspect_ref() {
  local ref="$1" optional="${2:-false}" value error_file
  error_file=$(mktemp "${RUNNER_TEMP:?}/marketry-image-inspect.XXXXXX")
  if value=$(docker buildx imagetools inspect --format '{{.Manifest.Digest}}' "$ref" 2>"$error_file"); then
    rm -f "$error_file"
    [[ "$value" =~ ^sha256:[a-f0-9]{64}$ ]] || { echo "잘못된 이미지 다이제스트: $ref" >&2; return 1; }
    printf '%s\n' "$value"
  else
    if [ "$optional" = true ] && { grep -Fxq "ERROR: $ref: not found" "$error_file" || grep -Eq '(^|: )manifest unknown(:|$)' "$error_file"; }; then
      rm -f "$error_file"
      return 0
    fi
    rm -f "$error_file"
    echo "이미지 조회 실패(권한·통신·존재 여부 확인): $ref" >&2
    return 1
  fi
}

copy_missing() {
  local source="$1" target="$2" tag="$3" optional="${4:-false}" existing original copied staged
  existing=$(inspect_ref "$target:$tag" true) || return 1
  if [ -n "$existing" ]; then
    printf 'KEEP %s:%s %s\n' "$target" "$tag" "$existing"
    return 0
  fi
  original=$(inspect_ref "$source:$tag" "$optional") || return 1
  if [ -z "$original" ]; then
    printf 'ABSENT optional %s:%s\n' "$source" "$tag"
    return 0
  fi
  # 매니페스트 목록과 플랫폼을 보존한다. 단일 이미지도 새 index로 감싸지 않는다.
  staged="migration-${original#sha256:}"
  docker buildx imagetools create --prefer-index=false -t "$target:$staged" "$source@$original" || return 1
  copied=$(inspect_ref "$target:$staged") || return 1
  [ "$copied" = "$original" ] || { echo "이전 임시 태그 다이제스트 불일치: $target:$staged" >&2; return 1; }
  docker buildx imagetools create --prefer-index=false -t "$target:$tag" "$target@$original" || return 1
  copied=$(inspect_ref "$target:$tag") || return 1
  [ "$copied" = "$original" ] || { echo "이전 다이제스트 불일치: $target:$tag" >&2; return 1; }
  printf 'COPIED %s:%s -> %s:%s %s\n' "$source" "$tag" "$target" "$tag" "$copied"
}

main() {
  local registry=ghcr.io/fwangchanju
  # 새 빌드 main/latest와 이미 운영 중인 새 deployed/previous를 덮어쓰지 않는다.
  copy_missing "$registry/market-monitor" "$registry/marketry-application" deployed
  copy_missing "$registry/market-monitor" "$registry/marketry-application" previous true
  copy_missing "$registry/market-monitor" "$registry/marketry-application" main
  copy_missing "$registry/market-monitor-assets" "$registry/marketry-assets" latest
  copy_missing "$registry/market-monitor-nginx" "$registry/marketry-nginx" latest
  copy_missing "$registry/market-monitor-renderer" "$registry/marketry-renderer" latest
}

main "$@"
