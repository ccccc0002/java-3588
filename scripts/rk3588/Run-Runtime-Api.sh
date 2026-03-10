#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
REPO_ROOT="$(cd "${SCRIPT_DIR}/../.." && pwd)"
RUNTIME_DIR="${RUNTIME_DIR:-${REPO_ROOT}/runtime}"
RUNTIME_CONFIG_FILE="${RUNTIME_CONFIG_FILE:-${RUNTIME_DIR}/config/application-rk3588.yml}"
FALLBACK_SCRIPT="${FALLBACK_SCRIPT:-${RUNTIME_DIR}/start-runtime-18081.sh}"
APP_JAR="${APP_JAR:-${REPO_ROOT}/target/java-rk3588-0.0.1-SNAPSHOT.jar}"
JAVA_BIN="${JAVA_BIN:-java}"
LOG_DIR="${LOG_DIR:-${RUNTIME_DIR}/logs}"
LOG_FILE="${LOG_FILE:-${LOG_DIR}/app-18081-managed.log}"

JAVA_OPTS="${JAVA_OPTS:-}"
RUNTIME_BOOTSTRAP_TOKEN="${RUNTIME_BOOTSTRAP_TOKEN:-}"

if [[ -n "${RUNTIME_BOOTSTRAP_TOKEN}" ]]; then
  JAVA_OPTS="${JAVA_OPTS} -Druntime.bootstrap.token=${RUNTIME_BOOTSTRAP_TOKEN}"
fi

mkdir -p "${LOG_DIR}"

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
