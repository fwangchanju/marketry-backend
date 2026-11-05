#!/bin/bash
# 일일 백업과 개발용 스냅샷. deploy 계정의 LaunchDaemon(kr.co.marketry.backup)이 매일 04:30에 실행한다.
# 레포 밖(~/Optional/bin/marketry-backup)에 설치해서 쓴다. 이 파일은 설치본의 원본이다.
#
# ① 일일 덤프(7일 보관)  ② R2 업로드(설정이 있을 때만)  ③ 개발용 스냅샷
# ②와 ③이 실패해도 ①의 백업은 남는다. 단계마다 실패를 직접 처리하므로 set -e는 쓰지 않는다.
set -uo pipefail

export PATH="$HOME/Optional/bin:$HOME/Optional/lima/bin:/usr/bin:/bin:/usr/sbin:/sbin"
export DOCKER_HOST="unix:///Users/deploy/.colima/default/docker.sock"

ENV_FILE="${MARKETRY_ENV_FILE:-$HOME/Projects/marketry/env/marketry.env}"
DAILY_DIR="$HOME/Projects/marketry/backups/daily"
KEEP_DAYS=7
SNAPSHOT_DIR="/Users/chanju/Projects/MARKETRY/db-dump"
SNAPSHOT_DB="marketry_snapshot"
RCLONE_BIN="$HOME/Optional/bin/rclone"

log() {
  echo "[$(date '+%Y-%m-%d %H:%M:%S')] $*"
}

# env 파일에서 키 하나의 값을 읽는다. 파일을 셸로 실행하지 않는다
env_value() {
  local line
  line=$(grep -E "^$1=" "$ENV_FILE" 2>/dev/null | tail -n 1) || true
  line=${line#*=}
  line=${line%\"}
  line=${line#\"}
  line=${line%\'}
  line=${line#\'}
  printf '%s' "$line"
}

daily_dump() {
  local final tmp
  mkdir -p "$DAILY_DIR"
  chmod 700 "$DAILY_DIR"
  final="$DAILY_DIR/daily-$(date +%Y%m%d).dump"
  tmp="$final.tmp"

  log "일일 덤프: $final"
  if ! docker exec marketry-postgres pg_dump -U marketry -Fc marketry_db >"$tmp" || [ ! -s "$tmp" ]; then
    rm -f "$tmp"
    log "오류: 일일 덤프 실패"
    return 1
  fi
  mv "$tmp" "$final"

  find "$DAILY_DIR" -maxdepth 1 -name 'daily-*.dump' -mtime "+$((KEEP_DAYS - 1))" -delete
  DAILY_DUMP="$final"
}

# 보관 10일 정리는 R2 버킷의 수명 주기 규칙에 맡긴다. 여기서는 올리기만 한다
upload_r2() {
  local endpoint bucket key secret
  endpoint=$(env_value R2_ENDPOINT)
  bucket=$(env_value R2_BUCKET)
  key=$(env_value R2_ACCESS_KEY_ID)
  secret=$(env_value R2_SECRET_ACCESS_KEY)

  if [ -z "$endpoint" ] || [ -z "$bucket" ] || [ -z "$key" ] || [ -z "$secret" ]; then
    log "R2 업로드 건너뜀: R2_* 설정이 없습니다"
    return 0
  fi
  if [ ! -x "$RCLONE_BIN" ]; then
    log "R2 업로드 건너뜀: $RCLONE_BIN 이(가) 없습니다"
    return 0
  fi

  log "R2 업로드: $bucket/daily/"
  RCLONE_CONFIG_R2_TYPE=s3 \
    RCLONE_CONFIG_R2_PROVIDER=Cloudflare \
    RCLONE_CONFIG_R2_ENDPOINT="$endpoint" \
    RCLONE_CONFIG_R2_ACCESS_KEY_ID="$key" \
    RCLONE_CONFIG_R2_SECRET_ACCESS_KEY="$secret" \
    RCLONE_CONFIG_R2_NO_CHECK_BUCKET=true \
    "$RCLONE_BIN" copyto "$DAILY_DUMP" "R2:$bucket/daily/$(basename "$DAILY_DUMP")"
}

drop_snapshot_db() {
  docker exec marketry-postgres psql -U marketry -d postgres -v ON_ERROR_STOP=1 -q \
    -c "DROP DATABASE IF EXISTS $SNAPSHOT_DB WITH (FORCE)"
}

# 개인 정보를 가린 개발용 스냅샷을 만든다.
# 가리는 대상은 users(email, sub)와 user_profile(nickname, image)이다. issuer는 값이 Google 주소 하나라 둔다.
# user_refresh_token은 데이터만 뺀다(테이블까지 빼면 엔티티 검증과 dev-login이 깨진다).
make_snapshot() {
  local tmp="$SNAPSHOT_DIR/latest.dump.tmp"

  mkdir -p "$SNAPSHOT_DIR" || return 1
  chmod 755 "$SNAPSHOT_DIR" || return 1

  log "개발용 스냅샷 시작"
  drop_snapshot_db || return 1
  docker exec marketry-postgres psql -U marketry -d postgres -v ON_ERROR_STOP=1 -q \
    -c "CREATE DATABASE $SNAPSHOT_DB" || return 1

  if ! docker exec marketry-postgres pg_dump -U marketry -Fc --exclude-table-data=user_refresh_token marketry_db |
    docker exec -i marketry-postgres pg_restore -U marketry -d "$SNAPSHOT_DB" --no-owner --exit-on-error; then
    log "오류: 스냅샷 DB 복원 실패"
    drop_snapshot_db || true
    return 1
  fi

  if ! docker exec -i marketry-postgres psql -U marketry -d "$SNAPSHOT_DB" -v ON_ERROR_STOP=1 -q <<'SQL'
BEGIN;
UPDATE users
   SET email = 'user' || id || '@example.invalid',
       sub = 'sub-' || id;
UPDATE user_profile
   SET nickname = 'user' || user_id
 WHERE nickname IS NOT NULL;
UPDATE user_profile
   SET image = NULL,
       image_updated_at = NULL;
COMMIT;
SQL
  then
    log "오류: 개인 정보 마스킹 실패"
    drop_snapshot_db || true
    return 1
  fi

  if ! docker exec marketry-postgres pg_dump -U marketry -Fc "$SNAPSHOT_DB" >"$tmp" || [ ! -s "$tmp" ]; then
    log "오류: 스냅샷 덤프 실패"
    rm -f "$tmp"
    drop_snapshot_db || true
    return 1
  fi
  if ! chmod 644 "$tmp" || ! mv "$tmp" "$SNAPSHOT_DIR/latest.dump"; then
    log "오류: 개발용 스냅샷 파일 교체 실패"
    rm -f "$tmp"
    drop_snapshot_db || true
    return 1
  fi

  drop_snapshot_db || true
  log "개발용 스냅샷 완료: $SNAPSHOT_DIR/latest.dump"
}

DAILY_DUMP=""
status=0

if daily_dump; then
  upload_r2 || {
    log "오류: R2 업로드 실패"
    status=1
  }
else
  status=1
fi

make_snapshot || {
  log "오류: 개발용 스냅샷 실패"
  status=1
}

exit "$status"
