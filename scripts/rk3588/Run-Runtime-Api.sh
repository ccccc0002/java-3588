#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
REPO_ROOT="$(cd "${SCRIPT_DIR}/../.." && pwd)"
RUNTIME_DIR="${RUNTIME_DIR:-${REPO_ROOT}/runtime}"
RUNTIME_CONFIG_FILE="${RUNTIME_CONFIG_FILE:-${RUNTIME_DIR}/config/application-rk3588.yml}"
FALLBACK_SCRIPT="${FALLBACK_SCRIPT:-${RUNTIME_DIR}/start-runtime-18081.sh}"
APP_JAR="${APP_JAR:-${REPO_ROOT}/target/java-rk3588-0.0.1-SNAPSHOT.jar}"
JAVA_BIN="${JAVA_BIN:-java}"
PYTHON_BIN="${PYTHON_BIN:-python3}"
FALLBACK_API_SCRIPT="${FALLBACK_API_SCRIPT:-${REPO_ROOT}/scripts/rk3588/runtime_api_fallback.py}"
LOG_DIR="${LOG_DIR:-${RUNTIME_DIR}/logs}"
LOG_FILE="${LOG_FILE:-${LOG_DIR}/app-18081-managed.log}"
RUNTIME_API_BACKEND="${RUNTIME_API_BACKEND:-java}"
RUNTIME_API_HOST="${RUNTIME_API_HOST:-127.0.0.1}"
RUNTIME_API_PORT="${RUNTIME_API_PORT:-18081}"
RUNTIME_BRIDGE_HEALTH_URL="${RUNTIME_BRIDGE_HEALTH_URL:-http://127.0.0.1:19080/health}"
RUNTIME_BRIDGE_TIMEOUT_SEC="${RUNTIME_BRIDGE_TIMEOUT_SEC:-5}"
RUNTIME_TOKEN_TTL_SEC="${RUNTIME_TOKEN_TTL_SEC:-3600}"
RUNTIME_API_SNAPSHOT_PATH="${RUNTIME_API_SNAPSHOT_PATH:-${RUNTIME_DIR}/runtime-api-fallback-snapshot.json}"

JAVA_OPTS="${JAVA_OPTS:-}"
RUNTIME_BOOTSTRAP_TOKEN="${RUNTIME_BOOTSTRAP_TOKEN:-}"

if [[ -n "${RUNTIME_BOOTSTRAP_TOKEN}" ]]; then
  JAVA_OPTS="${JAVA_OPTS} -Druntime.bootstrap.token=${RUNTIME_BOOTSTRAP_TOKEN}"
fi

mkdir -p "${LOG_DIR}"

if [[ "${RUNTIME_API_BACKEND}" == "python_fallback" ]]; then
  if [[ ! -f "${FALLBACK_API_SCRIPT}" ]]; then
    echo "missing python fallback script: ${FALLBACK_API_SCRIPT}" >&2
    exit 1
  fi
  exec "${PYTHON_BIN}" "${FALLBACK_API_SCRIPT}" \
    --host "${RUNTIME_API_HOST}" \
    --port "${RUNTIME_API_PORT}" \
    --bootstrap-token "${RUNTIME_BOOTSTRAP_TOKEN}" \
    --bridge-health-url "${RUNTIME_BRIDGE_HEALTH_URL}" \
    --bridge-timeout-sec "${RUNTIME_BRIDGE_TIMEOUT_SEC}" \
    --token-ttl-sec "${RUNTIME_TOKEN_TTL_SEC}" \
    --snapshot-path "${RUNTIME_API_SNAPSHOT_PATH}" \
    >>"${LOG_FILE}" 2>&1
fi

if [[ ! -f "${APP_JAR}" ]]; then
  echo "missing app jar: ${APP_JAR}" >&2
  exit 1
fi

if [[ ! -f "${RUNTIME_CONFIG_FILE}" ]]; then
  if [[ -x "${FALLBACK_SCRIPT}" ]]; then
    exec bash "${FALLBACK_SCRIPT}"
  fi
  echo "missing runtime config file: ${RUNTIME_CONFIG_FILE}" >&2
  exit 1
fi

exec "${JAVA_BIN}" ${JAVA_OPTS} -jar "${APP_JAR}" \
  "--spring.config.additional-location=file:${RUNTIME_CONFIG_FILE}" \
  >>"${LOG_FILE}" 2>&1
