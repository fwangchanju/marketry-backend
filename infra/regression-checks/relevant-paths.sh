#!/bin/bash
# git diff --name-only 입력에서 상시 인프라 회귀 검증 대상 변경을 고른다.
set -euo pipefail
while IFS= read -r path; do
  case "$path" in
    .github/workflows/*|infra/regression-checks/*|infra/prod/*.sh|infra/prod/compose.yml|infra/prod/nginx.conf|infra/scripts/*.sh|infra/local/*.sh|infra/local/compose.yml|containers/nginx/Dockerfile)
      exit 0 ;;
  esac
done
exit 1
