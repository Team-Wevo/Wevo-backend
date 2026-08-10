#!/usr/bin/env bash

set -Eeuo pipefail

readonly ENV_FILE="${1:?production env file is required}"

if [[ ! -f "$ENV_FILE" ]]; then
  echo "Production env file does not exist." >&2
  exit 1
fi

read_single_env_value() {
  local variable_name="$1"

  awk -v variable_name="$variable_name" '
    index($0, variable_name "=") == 1 {
      count++
      value = substr($0, length(variable_name) + 2)
    }
    END {
      if (count != 1 || value == "") {
        exit 1
      }
      print value
    }
  ' "$ENV_FILE"
}

if ! ai_provider="$(read_single_env_value AI_PROVIDER)"; then
  echo "AI_PROVIDER must be declared exactly once with a non-empty value." >&2
  exit 1
fi

if ! guardrail_enabled="$(read_single_env_value AI_GUARDRAIL_ENABLED)"; then
  echo "AI_GUARDRAIL_ENABLED must be declared exactly once with a non-empty value." >&2
  exit 1
fi

case "$ai_provider" in
  openai | none) ;;
  *)
    echo "AI_PROVIDER must be openai or none." >&2
    exit 1
    ;;
esac

case "$guardrail_enabled" in
  true | false) ;;
  *)
    echo "AI_GUARDRAIL_ENABLED must be true or false." >&2
    exit 1
    ;;
esac

if [[ "$ai_provider" == "openai" && "$guardrail_enabled" != "true" ]]; then
  echo "AI_GUARDRAIL_ENABLED=true is required when AI_PROVIDER=openai." >&2
  exit 1
fi

echo "Production AI environment preflight passed."
