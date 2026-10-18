#!/bin/bash
set -e

source "$HOME/repo/market-monitor-backend/infra/scripts/env.sh"

NGINX_COMPOSE_FILE="$REPO_DIR/infra/nginx-docker-compose.yml"
: "${IMAGE_TAG:?IMAGE_TAG가 지정되지 않았습니다}"
export IMAGE_TAG

echo "=== [nginx] Logging in to GHCR ==="
echo "$CR_PAT" | docker login ghcr.io -u "$GHCR_USER" --password-stdin

echo "=== [nginx] Pulling image (tag: $IMAGE_TAG) ==="
docker pull "ghcr.io/$GHCR_USER/marketry-nginx:$IMAGE_TAG"

echo "=== [nginx] Restarting nginx ==="
docker compose -f "$NGINX_COMPOSE_FILE" --env-file "$ENV_FILE" down
docker compose -f "$NGINX_COMPOSE_FILE" --env-file "$ENV_FILE" up -d

echo "=== [nginx] 로컬 이미지 정리 ==="
docker image prune -f

echo "=== [nginx] Done ==="
