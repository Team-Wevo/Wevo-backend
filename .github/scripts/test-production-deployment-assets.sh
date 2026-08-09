#!/usr/bin/env bash

set -Eeuo pipefail

readonly DEPLOY_SCRIPT=".github/scripts/deploy-on-ec2.sh"
readonly DEPLOY_WORKFLOW=".github/workflows/deploy.yml"
readonly COMPOSE_FILE="compose.prod.yml"
readonly EXAMPLE_ENV_FILE=".env.example"

assert_contains() {
  local file="$1"
  local expected="$2"

  if ! grep -Fq "$expected" "$file"; then
    echo "$file must contain: $expected" >&2
    exit 1
  fi
}

assert_not_contains() {
  local file="$1"
  local unexpected="$2"

  if grep -Fq "$unexpected" "$file"; then
    echo "$file must not contain legacy AI Provider setting: $unexpected" >&2
    exit 1
  fi
}

bash -n "$DEPLOY_SCRIPT"

assert_contains "$DEPLOY_SCRIPT" 'SOURCE_COMPOSE_FILE="${4:?compose file from the deployment commit is required}"'
assert_contains "$DEPLOY_SCRIPT" 'docker compose --env-file "$ENV_FILE" -f "$SOURCE_COMPOSE_FILE" config --quiet'
assert_contains "$DEPLOY_WORKFLOW" 'compose_payload="$(gzip -c compose.prod.yml | base64 -w 0)"'
assert_contains "$DEPLOY_WORKFLOW" '/tmp/wevo-compose.prod.yml'

preflight_line="$(grep -nF 'docker compose --env-file "$ENV_FILE" -f "$SOURCE_COMPOSE_FILE" config --quiet' \
  "$DEPLOY_SCRIPT" | head -n1 | cut -d: -f1)"
pull_line="$(grep -nF 'docker compose --env-file "$ENV_FILE" -f "$SOURCE_COMPOSE_FILE" pull app' \
  "$DEPLOY_SCRIPT" | head -n1 | cut -d: -f1)"
recreate_line="$(grep -nF 'docker compose --env-file "$ENV_FILE" -f "$COMPOSE_FILE" up -d --force-recreate app' \
  "$DEPLOY_SCRIPT" | head -n1 | cut -d: -f1)"

if (( preflight_line >= pull_line || preflight_line >= recreate_line )); then
  echo "production compose preflight must run before image pull and app recreation." >&2
  exit 1
fi

test_directory="$(mktemp -d)"
trap 'rm -rf "$test_directory"' EXIT
readonly BASELINE_ENV_FILE="$test_directory/baseline.env"
awk '{
  if ($0 == "OPENAI_API_KEY=") {
    print "OPENAI_API_KEY=ci-openai-key"
  } else {
    print
  }
}' "$EXAMPLE_ENV_FILE" > "$BASELINE_ENV_FILE"

env APP_IMAGE=wevo-backend:ci \
  docker compose --env-file "$BASELINE_ENV_FILE" -f "$COMPOSE_FILE" config --quiet

readonly REQUIRED_PRODUCTION_VARIABLES=(
  DB_URL
  DB_USERNAME
  DB_PASSWORD
  REDIS_PASSWORD
  JWT_SECRET
  FRONTEND_ORIGIN
  INVITE_BASE_URL
  INVITE_TOKEN_SECRET
  AI_PROVIDER
  OPENAI_API_KEY
  OPENAI_API_MODEL
)

for variable_name in "${REQUIRED_PRODUCTION_VARIABLES[@]}"; do
  missing_variable_env="$test_directory/missing-${variable_name}.env"
  compose_error="$test_directory/${variable_name}-compose-error.log"
  awk -v prefix="${variable_name}=" 'index($0, prefix) != 1' \
    "$BASELINE_ENV_FILE" > "$missing_variable_env"

  if env APP_IMAGE=wevo-backend:ci \
    docker compose --env-file "$missing_variable_env" -f "$COMPOSE_FILE" config --quiet \
    > /dev/null 2> "$compose_error"; then
    echo "compose validation must fail before deployment when $variable_name is missing." >&2
    exit 1
  fi

  if ! grep -Fq "$variable_name must be set" "$compose_error"; then
    echo "compose validation for $variable_name failed for an unexpected reason." >&2
    exit 1
  fi
done

assert_contains "$EXAMPLE_ENV_FILE" 'AI_PROVIDER=openai'
assert_not_contains "$COMPOSE_FILE" 'NVIDIA_API_'
assert_not_contains "$EXAMPLE_ENV_FILE" 'NVIDIA_API_'
assert_not_contains "src/main/resources/application.yml" 'wevo.ai.nvidia'

echo "Production deployment asset contract passed."
