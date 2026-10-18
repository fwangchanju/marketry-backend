#!/bin/bash
# GHCR 호출을 모조해 이전의 재실행·기존 포인터 보존·실패 차단을 검증한다.
set -euo pipefail
repo="$(cd "$(dirname "$0")/../.." && pwd)"
workflow="$repo/.github/workflows/migrate-image-names.yml"
grep -Fq 'packages: write' "$workflow"
grep -Fq 'username: ${{ github.actor }}' "$workflow"
grep -Fq 'password: ${{ secrets.GITHUB_TOKEN }}' "$workflow"
! grep -Fq 'secrets.CR_PAT' "$workflow"
echo 'PASS: GHCR 마이그레이션은 packages:write가 있는 workflow token 사용'
root=$(mktemp -d /tmp/marketry-image-names.XXXXXX)
trap 'rm -rf "$root"' EXIT
export MOCK_ROOT="$root" RUNNER_TEMP="$root"
sed '/^main "\$@"$/d' "$repo/infra/scripts/migrate-image-names.sh" > "$root/functions.sh"
source "$root/functions.sh"
digest() { printf 'sha256:%064x' "$1"; }
ref_file() { printf '%s/%s' "$MOCK_ROOT" "${1//\//_}"; }
docker() {
  printf '%s\n' "$*" >> "$MOCK_ROOT/trace"
  [ "$1 $2" = 'buildx imagetools' ] || return 88
  case "$3" in
    inspect)
      [ "$4" = --format ] || return 88
      local ref="$6" file
      file=$(ref_file "$ref")
      if [ -e "$MOCK_ROOT/auth-error" ]; then echo 'ERROR: unauthorized' >&2; return 1; fi
      if [ -e "$file" ]; then cat "$file"; else echo "ERROR: $ref: not found" >&2; return 1; fi ;;
    create)
      [ "$4" = --prefer-index=false ] && [ "$5" = -t ] || return 88
      [ ! -e "$MOCK_ROOT/copy-error" ] || return 1
      if [ -e "$MOCK_ROOT/wrong-copy" ]; then digest 999 > "$(ref_file "$6")"; else printf '%s\n' "${7##*@}" > "$(ref_file "$6")"; fi ;;
    *) return 88 ;;
  esac
}
old=ghcr.io/fwangchanju/market-monitor
new=ghcr.io/fwangchanju/marketry-application
for tag in deployed previous main; do digest 1 > "$(ref_file "$old:$tag")"; done
for suffix in assets nginx renderer; do digest 2 > "$(ref_file "$old-$suffix:latest")"; done
main > /dev/null
[ "$(cat "$(ref_file "$new:deployed")")" = "$(digest 1)" ]
: > "$root/trace"
main > /dev/null
! grep -q ' create ' "$root/trace"
echo 'PASS: 다이제스트 검증과 재실행 시 복사 0회'
digest 3 > "$(ref_file "$new:main")"
main > /dev/null
[ "$(cat "$(ref_file "$new:main")")" = "$(digest 3)" ]
echo 'PASS: 새 빌드 main과 이미 이전한 정상 포인터 보존'
rm "$(ref_file "$old:previous")" "$(ref_file "$new:previous")"
copy_missing "$old" "$new" previous true > /dev/null
echo 'PASS: 선택 포인터 previous의 실제 부재 허용'
touch "$root/auth-error"
if copy_missing "$old" "$new" previous true >/dev/null 2>&1; then exit 1; fi
rm "$root/auth-error"
touch "$root/copy-error"
if copy_missing "$old" "$new" fresh >/dev/null 2>&1; then exit 1; fi
# source가 실제 존재하는 복사의 실패도 확인한다.
digest 4 > "$(ref_file "$old:fresh")"
if copy_missing "$old" "$new" fresh >/dev/null 2>&1; then exit 1; fi
rm "$root/copy-error"
echo 'PASS: 인증·필수 source 부재·복사 실패 차단'
touch "$root/wrong-copy"
if copy_missing "$old" "$new" fresh >/dev/null 2>&1; then exit 1; fi
[ ! -e "$(ref_file "$new:fresh")" ]
rm "$root/wrong-copy"
echo 'PASS: 복사 후 다이제스트 불일치 차단'
echo '5개 이미지 이름 이전 회귀 검증 통과'
