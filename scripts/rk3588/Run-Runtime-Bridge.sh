#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
REPO_ROOT="$(cd "${SCRIPT_DIR}/../.." && pwd)"

RUNTIME_URL="${RUNTIME_URL:-http://127.0.0.1:18081}"
LISTEN_HOST="${LISTEN_HOST:-0.0.0.0}"
LISTEN_PORT="${LISTEN_PORT:-19080}"
RUNTIME_TOKEN="${RUNTIME_TOKEN:-}"
RUNTIME_BOOTSTRAP_TOKEN="${RUNTIME_BOOTSTRAP_TOKEN:-}"
RUNTIME_TOKEN_ROLE="${RUNTIME_TOKEN_ROLE:-admin}"
RUNTIME_USER_ID="${RUNTIME_USER_ID:-java-rk3588-bridge}"
BOOTSTRAP_HEADER_NAME="${BOOTSTRAP_HEADER_NAME:-X-Bootstrap-Token}"
TIMEOUT_SEC="${TIMEOUT_SEC:-5}"
PLAN_BUDGET="${PLAN_BUDGET:-10}"
DECODE_MODE="${DECODE_MODE:-stub}"
BRIDGE_VERSION="${BRIDGE_VERSION:-0.2.0}"

exec python3 "${REPO_ROOT}/scripts/rk3588/rk3588_runtime_bridge.py" \
  --listen-host "${LISTEN_HOST}" \
  --listen-port "${LISTEN_PORT}" \
  --runtime-url "${RUNTIME_URL}" \
  --runtime-token "${RUNTIME_TOKEN}" \
  --runtime-bootstrap-token "${RUNTIME_BOOTSTRAP_TOKEN}" \
  --runtime-token-role "${RUNTIME_TOKEN_ROLE}" \
  --runtime-user-id "${RUNTIME_USER_ID}" \
  --bootstrap-header-name "${BOOTSTRAP_HEADER_NAME}" \
  --timeout-sec "${TIMEOUT_SEC}" \
  --plan-budget "${PLAN_BUDGET}" \
  --decode-mode "${DECODE_MODE}" \
  --bridge-version "${BRIDGE_VERSION}"
