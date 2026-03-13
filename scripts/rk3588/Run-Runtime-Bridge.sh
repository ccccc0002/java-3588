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
DECODE_MODE="${DECODE_MODE:-mpp-rga}"
DECODE_FFMPEG_BIN="${DECODE_FFMPEG_BIN:-ffmpeg}"
PLUGINS_ROOT="${PLUGINS_ROOT:-${REPO_ROOT}/scripts/rk3588/plugins}"
DEFAULT_PLUGIN_ID="${DEFAULT_PLUGIN_ID:-yolov8n}"
EXPECTED_PLUGIN_RUNTIME="${EXPECTED_PLUGIN_RUNTIME:-rk3588_rknn}"
BRIDGE_VERSION="${BRIDGE_VERSION:-0.3.0}"

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
  --decode-ffmpeg-bin "${DECODE_FFMPEG_BIN}" \
  --plugins-root "${PLUGINS_ROOT}" \
  --default-plugin-id "${DEFAULT_PLUGIN_ID}" \
  --expected-plugin-runtime "${EXPECTED_PLUGIN_RUNTIME}" \
  --bridge-version "${BRIDGE_VERSION}"
