#!/usr/bin/env bash

set -Eeuo pipefail

readonly DEPLOY_SCRIPT=".github/scripts/deploy-on-ec2.sh"
readonly AI_ENV_VALIDATOR=".github/scripts/validate-production-ai-env.sh"
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
bash -n "$AI_ENV_VALIDATOR"

assert_contains "$DEPLOY_SCRIPT" 'SOURCE_COMPOSE_FILE="${4:?compose file from the deployment commit is required}"'
assert_contains "$DEPLOY_SCRIPT" 'AI_ENV_VALIDATOR="${5:?AI environment validator from the deployment commit is required}"'
assert_contains "$DEPLOY_SCRIPT" '"$AI_ENV_VALIDATOR" "$ENV_FILE"'
assert_contains "$DEPLOY_SCRIPT" 'docker compose --env-file "$ENV_FILE" -f "$SOURCE_COMPOSE_FILE" config --quiet'
assert_contains "$DEPLOY_WORKFLOW" 'compose_payload="$(gzip -c compose.prod.yml | base64 -w 0)"'
assert_contains "$DEPLOY_WORKFLOW" 'ai_env_validator_payload="$(gzip -c .github/scripts/validate-production-ai-env.sh | base64 -w 0)"'
assert_contains "$DEPLOY_WORKFLOW" '/tmp/wevo-compose.prod.yml'
assert_contains "$DEPLOY_WORKFLOW" '/tmp/wevo-validate-production-ai-env.sh'

preflight_line="$(grep -nF 'docker compose --env-file "$ENV_FILE" -f "$SOURCE_COMPOSE_FILE" config --quiet' \
  "$DEPLOY_SCRIPT" | head -n1 | cut -d: -f1)"
ai_preflight_line="$(grep -nF '"$AI_ENV_VALIDATOR" "$ENV_FILE"' \
  "$DEPLOY_SCRIPT" | head -n1 | cut -d: -f1)"
pull_line="$(grep -nF 'docker compose --env-file "$ENV_FILE" -f "$SOURCE_COMPOSE_FILE" pull app' \
  "$DEPLOY_SCRIPT" | head -n1 | cut -d: -f1)"
recreate_line="$(grep -nF 'docker compose --env-file "$ENV_FILE" -f "$COMPOSE_FILE" up -d --force-recreate app' \
  "$DEPLOY_SCRIPT" | head -n1 | cut -d: -f1)"

if (( ai_preflight_line >= pull_line || ai_preflight_line >= recreate_line \
  || preflight_line >= pull_line || preflight_line >= recreate_line )); then
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

bash "$AI_ENV_VALIDATOR" "$BASELINE_ENV_FILE"

readonly DISABLED_GUARDRAIL_ENV_FILE="$test_directory/disabled-guardrail.env"
awk '{
  if ($0 == "AI_GUARDRAIL_ENABLED=true") {
    print "AI_GUARDRAIL_ENABLED=false"
  } else {
    print
  }
}' "$BASELINE_ENV_FILE" > "$DISABLED_GUARDRAIL_ENV_FILE"

if bash "$AI_ENV_VALIDATOR" "$DISABLED_GUARDRAIL_ENV_FILE" > /dev/null 2>&1; then
  echo "production AI preflight must reject openai with disabled guardrails." >&2
  exit 1
fi

readonly DISABLED_PROVIDER_ENV_FILE="$test_directory/disabled-provider.env"
awk '{
  if ($0 == "AI_PROVIDER=openai") {
    print "AI_PROVIDER=none"
  } else if ($0 == "AI_GUARDRAIL_ENABLED=true") {
    print "AI_GUARDRAIL_ENABLED=false"
  } else {
    print
  }
}' "$BASELINE_ENV_FILE" > "$DISABLED_PROVIDER_ENV_FILE"

bash "$AI_ENV_VALIDATOR" "$DISABLED_PROVIDER_ENV_FILE"

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
  AI_GUARDRAIL_ENABLED
  OPENAI_API_KEY
  OPENAI_API_MODEL
)

readonly OPENAI_PRODUCTION_VARIABLES=(
  OPENAI_API_KEY
  OPENAI_API_BASE_URL
  OPENAI_API_MODEL
  OPENAI_API_TIMEOUT
  OPENAI_API_MAX_TOKENS
  OPENAI_API_REASONING_EFFORT
  OPENAI_API_CONTEXT_LIMIT
  OPENAI_PROMPT_CACHE_OPTIMIZATION_ENABLED
  OPENAI_PROMPT_CACHE_EXPLICIT_FEATURES
  OPENAI_PROMPT_CACHE_EXPLICIT_MODELS
  OPENAI_PROMPT_CACHE_TTL
)

for variable_name in "${OPENAI_PRODUCTION_VARIABLES[@]}"; do
  assert_contains "$COMPOSE_FILE" "$variable_name: \${$variable_name"
done

for variable_name in "${REQUIRED_PRODUCTION_VARIABLES[@]}"; do
  missing_variable_env="$test_directory/missing-${variable_name}.env"
  compose_error="$test_directory/${variable_name}-compose-error.log"
  awk -v prefix="${variable_name}=" 'index($0, prefix) != 1' \
    "$BASELINE_ENV_FILE" > "$missing_variable_env"

  if env -u "$variable_name" APP_IMAGE=wevo-backend:ci \
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

# 실제 Provider 호출을 허용하는 테스트용 opt-in은 운영 컨테이너에 전달하지 않는다.
# 그 외 .env.example의 AI/OpenAI 변수는 Compose 계약에서 누락되면 CI가 실패해야 한다.
while IFS= read -r variable_name; do
  case "$variable_name" in
    OPENAI_INTEGRATION_ENABLED \
      | OPENAI_CACHE_INTEGRATION_ENABLED \
      | OPENAI_EVALUATION_ENABLED \
      | OPENAI_EVALUATION_MAX_FIXTURES \
      | OPENAI_EVALUATION_MAX_PROVIDER_REQUESTS \
      | OPENAI_EVALUATION_MAX_OUTPUT_TOKENS \
      | OPENAI_EVALUATION_DEADLINE \
      | OPENAI_EVALUATION_MAX_COST_USD)
      continue
      ;;
  esac

  expected_prefix="      ${variable_name}: "'${'"${variable_name}"
  assert_contains "$COMPOSE_FILE" "$expected_prefix"
done < <(awk -F= '/^(AI_|OPENAI_)[A-Z0-9_]*=/ {print $1}' "$EXAMPLE_ENV_FILE" | sort -u)

assert_contains "$EXAMPLE_ENV_FILE" 'AI_PROVIDER=openai'
assert_not_contains "$COMPOSE_FILE" 'NVIDIA_API_'
assert_not_contains "$EXAMPLE_ENV_FILE" 'NVIDIA_API_'
assert_not_contains "src/main/resources/application.yml" 'wevo.ai.nvidia'

echo "Production deployment asset contract passed."
