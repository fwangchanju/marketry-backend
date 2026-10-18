#!/bin/bash
set -e

source "$HOME/repo/market-monitor-backend/infra/scripts/env.sh"

COMPOSE_FILE="$REPO_DIR/infra/renderer-docker-compose.yml"
: "${IMAGE_TAG:?IMAGE_TAG가 지정되지 않았습니다}"
export IMAGE_TAG

echo "=== [renderer] Logging in to GHCR ==="
echo "$CR_PAT" | docker login ghcr.io -u "$GHCR_USER" --password-stdin

echo "=== [renderer] Pulling image (tag: $IMAGE_TAG) ==="
docker pull "ghcr.io/$GHCR_USER/marketry-renderer:$IMAGE_TAG"

echo "=== [renderer] Restarting market-monitor-renderer ==="
docker compose -f "$COMPOSE_FILE" --env-file "$ENV_FILE" up -d

echo "=== [renderer] 로컬 이미지 정리 ==="
docker image prune -f

echo "=== [renderer] Done ==="
