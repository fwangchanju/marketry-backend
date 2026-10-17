#!/bin/bash
set -e

source "$HOME/repo/market-monitor-backend/infra/scripts/env.sh"

COMPOSE_FILE="$REPO_DIR/infra/docker-compose.yml"
: "${IMAGE_TAG:?IMAGE_TAG가 지정되지 않았습니다}"
export IMAGE_TAG

echo "=== [application] Logging in to GHCR ==="
echo "$CR_PAT" | docker login ghcr.io -u "$GHCR_USER" --password-stdin

echo "=== [application] Pulling image (tag: $IMAGE_TAG) ==="
docker pull "ghcr.io/$GHCR_USER/marketry-application:$IMAGE_TAG"

echo "=== [application] Restarting market-monitor ==="
docker compose -f "$COMPOSE_FILE" --env-file "$ENV_FILE" up -d --no-deps market-monitor

echo "=== [application] 로컬 이미지 정리 ==="
CURRENT_IMAGE_ID=$(docker inspect --format='{{.Image}}' market-monitor)

RECENT_IMAGE_IDS=$(
  for image_id in $(docker images "ghcr.io/$GHCR_USER/marketry-application" -q | sort -u); do
    full_id=$(docker inspect --format='{{.Id}}' "$image_id")
    created=$(docker inspect --format='{{.Created}}' "$image_id")
    echo "$created $full_id"
  done | sort -r | head -2 | awk '{print $2}'
)
KEEP_IDS="$CURRENT_IMAGE_ID $RECENT_IMAGE_IDS"

for image_id in $(docker images "ghcr.io/$GHCR_USER/marketry-application" -q | sort -u); do
  full_id=$(docker inspect --format='{{.Id}}' "$image_id")
  if ! echo "$KEEP_IDS" | grep -q "$full_id"; then
    docker rmi -f "$image_id" || true
  fi
done

echo "=== [application] Done ==="
