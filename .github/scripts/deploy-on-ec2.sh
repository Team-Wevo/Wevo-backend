#!/usr/bin/env bash

set -Eeuo pipefail

IMAGE_URI="${1:?image URI is required}"
AWS_REGION="${2:?AWS region is required}"
ECR_REGISTRY="${3:?ECR registry is required}"
SOURCE_COMPOSE_FILE="${4:?compose file from the deployment commit is required}"
AI_ENV_VALIDATOR="${5:?AI environment validator from the deployment commit is required}"

DEPLOY_DIR="/opt/wevo"
COMPOSE_FILE="compose.prod.yml"
ENV_FILE=".env.prod"
HEALTH_URL="http://127.0.0.1:8081/actuator/health"
DEPLOYMENT_STARTED_AT="$(date --iso-8601=seconds)"
DEPLOYMENT_PHASE="initialization"

cd "$DEPLOY_DIR"

show_failure_context() {
  local exit_code=$?

  echo "Deployment failed during phase: $DEPLOYMENT_PHASE" >&2
  echo "Current container state:" >&2
  docker compose --env-file "$ENV_FILE" -f "$COMPOSE_FILE" ps >&2 || true
  echo "Application logs since this deployment started:" >&2
  docker compose --env-file "$ENV_FILE" -f "$COMPOSE_FILE" \
    logs --since "$DEPLOYMENT_STARTED_AT" --tail=500 app >&2 || true
  echo "Likely root-cause excerpt:" >&2
  docker compose --env-file "$ENV_FILE" -f "$COMPOSE_FILE" \
    logs --since "$DEPLOYMENT_STARTED_AT" --tail=500 app 2>/dev/null \
    | grep -m1 -B3 -A12 -E \
      'APPLICATION FAILED TO START|Caused by|Could not resolve placeholder|app\.invite\.token-secret|Flyway|Schema-validation' \
      >&2 || true

  exit "$exit_code"
}

trap show_failure_context ERR

DEPLOYMENT_PHASE="preflight"

if [[ ! -f "$SOURCE_COMPOSE_FILE" ]]; then
  echo "Deployment compose file does not exist: $SOURCE_COMPOSE_FILE" >&2
  exit 1
fi

if [[ ! -x "$AI_ENV_VALIDATOR" ]]; then
  echo "AI environment validator is not executable." >&2
  exit 1
fi

if [[ ! -f "$ENV_FILE" ]]; then
  echo "$DEPLOY_DIR/$ENV_FILE does not exist." >&2
  exit 1
fi

if [[ "$(stat -c '%a' "$ENV_FILE")" != "600" ]]; then
  echo "$DEPLOY_DIR/$ENV_FILE must have permission 600." >&2
  exit 1
fi

export APP_IMAGE="$IMAGE_URI"

# Provider와 guardrail 조합을 값 노출 없이 확인하고, 실패하면 기존 app을 유지한다.
"$AI_ENV_VALIDATOR" "$ENV_FILE"

# The compose file is shipped from the same commit as the immutable image. Validate all strict
# ${VAR:?message} expressions against the server-only env file before replacing a healthy app.
docker compose --env-file "$ENV_FILE" -f "$SOURCE_COMPOSE_FILE" config --quiet

compose_sha256="$(sha256sum "$SOURCE_COMPOSE_FILE" | awk '{print $1}')"
echo "Deployment preflight passed for compose SHA-256: $compose_sha256"

DEPLOYMENT_PHASE="registry-login"
aws ecr get-login-password --region "$AWS_REGION" \
  | docker login --username AWS --password-stdin "$ECR_REGISTRY"

DEPLOYMENT_PHASE="preserve-current-image"
current_container="$(docker compose --env-file "$ENV_FILE" -f "$COMPOSE_FILE" ps -q app || true)"
if [[ -n "$current_container" ]]; then
  current_image_id="$(docker inspect --format '{{.Image}}' "$current_container")"
  docker image tag "$current_image_id" wevo-backend:rollback
  echo "Saved the current application image as wevo-backend:rollback."
fi

DEPLOYMENT_PHASE="pull-image"
docker compose --env-file "$ENV_FILE" -f "$SOURCE_COMPOSE_FILE" pull app

DEPLOYMENT_PHASE="install-compose"
next_compose_file="${COMPOSE_FILE}.next"
previous_compose_file="${COMPOSE_FILE}.previous"

install -m 0644 "$SOURCE_COMPOSE_FILE" "$next_compose_file"
if [[ -f "$COMPOSE_FILE" ]]; then
  cp -p "$COMPOSE_FILE" "$previous_compose_file"
fi
mv -f "$next_compose_file" "$COMPOSE_FILE"
echo "Installed compose.prod.yml from the deployment commit ($compose_sha256)."

docker compose --env-file "$ENV_FILE" -f "$COMPOSE_FILE" config --quiet

DEPLOYMENT_PHASE="recreate-app"
docker compose --env-file "$ENV_FILE" -f "$COMPOSE_FILE" up -d --force-recreate app

DEPLOYMENT_PHASE="health-check"
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
