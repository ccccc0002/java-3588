#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
REPO_ROOT="$(cd "${SCRIPT_DIR}/../.." && pwd)"

RUNTIME_URL="${RUNTIME_URL:-http://127.0.0.1:18081}"
RUNTIME_TOKEN="${RUNTIME_TOKEN:-}"
RUNTIME_BOOTSTRAP_TOKEN="${RUNTIME_BOOTSTRAP_TOKEN:-}"
TIMEOUT_SEC="${TIMEOUT_SEC:-5}"
INTERVAL_SEC="${INTERVAL_SEC:-2}"

exec python3 "${REPO_ROOT}/scripts/rk3588/media_worker.py" \
  --runtime-url "${RUNTIME_URL}" \
  --runtime-token "${RUNTIME_TOKEN}" \
  --bootstrap-token "${RUNTIME_BOOTSTRAP_TOKEN}" \
  --timeout-sec "${TIMEOUT_SEC}" \
  --interval-sec "${INTERVAL_SEC}"