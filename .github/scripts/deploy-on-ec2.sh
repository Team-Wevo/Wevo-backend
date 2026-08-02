#!/usr/bin/env bash

set -Eeuo pipefail

IMAGE_URI="${1:?image URI is required}"
AWS_REGION="${2:?AWS region is required}"
ECR_REGISTRY="${3:?ECR registry is required}"

DEPLOY_DIR="/opt/wevo"
COMPOSE_FILE="compose.prod.yml"
ENV_FILE=".env.prod"
HEALTH_URL="http://127.0.0.1:8081/actuator/health"

cd "$DEPLOY_DIR"

show_failure_context() {
  local exit_code=$?

  echo "Deployment failed. Current container state:" >&2
  docker compose --env-file "$ENV_FILE" -f "$COMPOSE_FILE" ps >&2 || true
  echo "Recent application logs:" >&2
  docker compose --env-file "$ENV_FILE" -f "$COMPOSE_FILE" logs --tail=100 app >&2 || true

  exit "$exit_code"
}

trap show_failure_context ERR

if [[ "$(stat -c '%a' "$ENV_FILE")" != "600" ]]; then
  echo "$DEPLOY_DIR/$ENV_FILE must have permission 600." >&2
  exit 1
fi

aws ecr get-login-password --region "$AWS_REGION" \
  | docker login --username AWS --password-stdin "$ECR_REGISTRY"

current_container="$(docker compose --env-file "$ENV_FILE" -f "$COMPOSE_FILE" ps -q app || true)"
if [[ -n "$current_container" ]]; then
  current_image_id="$(docker inspect --format '{{.Image}}' "$current_container")"
  docker image tag "$current_image_id" wevo-backend:rollback
  echo "Saved the current application image as wevo-backend:rollback."
fi

export APP_IMAGE="$IMAGE_URI"

docker compose --env-file "$ENV_FILE" -f "$COMPOSE_FILE" config --quiet
docker compose --env-file "$ENV_FILE" -f "$COMPOSE_FILE" pull app
docker compose --env-file "$ENV_FILE" -f "$COMPOSE_FILE" up -d --force-recreate app

for attempt in $(seq 1 36); do
  if health_response="$(
    docker compose --env-file "$ENV_FILE" -f "$COMPOSE_FILE" exec -T app \
      wget -qO- "$HEALTH_URL" 2>/dev/null
  )" && grep -q '"status":"UP"' <<<"$health_response"; then
    echo "Application health check passed."
    docker compose --env-file "$ENV_FILE" -f "$COMPOSE_FILE" ps
    exit 0
  fi

  echo "Waiting for application health check ($attempt/36)..."
  sleep 5
done

echo "Application did not become healthy within 180 seconds." >&2
false
