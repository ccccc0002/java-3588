#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
REPO_ROOT="$(cd "${SCRIPT_DIR}/../.." && pwd)"
RUNTIME_DIR="${RUNTIME_DIR:-${REPO_ROOT}/runtime}"
APP_ENV_FILE="${APP_ENV_FILE:-${RUNTIME_DIR}/config/java-app.env}"
FALLBACK_SCRIPT="${FALLBACK_SCRIPT:-${RUNTIME_DIR}/start-app-18082.sh}"
APP_PORT="${APP_PORT:-18082}"
APP_JAR="${APP_JAR:-${REPO_ROOT}/target/java-rk3588-0.0.1-SNAPSHOT.jar}"
JAVA_BIN="${JAVA_BIN:-java}"
LOG_DIR="${LOG_DIR:-${RUNTIME_DIR}/logs}"
LOG_FILE="${LOG_FILE:-${LOG_DIR}/app-18082-managed.log}"
UPLOAD_DIR="${UPLOAD_DIR:-${RUNTIME_DIR}/data/upload/}"
CAMERA_DIR="${CAMERA_DIR:-${RUNTIME_DIR}/data/camera/}"
MODEL_DIR="${MODEL_DIR:-${RUNTIME_DIR}/data/model/}"
SELF_REPORT_URL="${SELF_REPORT_URL:-http://127.0.0.1:${APP_PORT}/api/report/push}"

mkdir -p "${LOG_DIR}" "${RUNTIME_DIR}/data/upload" "${RUNTIME_DIR}/data/camera" "${RUNTIME_DIR}/data/model"

if [[ -f "${APP_ENV_FILE}" ]]; then
  # shellcheck disable=SC1090
  source "${APP_ENV_FILE}"
fi

if [[ ! -f "${APP_JAR}" ]]; then
  if [[ -x "${FALLBACK_SCRIPT}" ]]; then
    exec bash "${FALLBACK_SCRIPT}"
  fi
  echo "missing app jar: ${APP_JAR}" >&2
  exit 1
fi

JAVA_ARGS=(
  "--server.port=${APP_PORT}"
  "--spring.sql.init.mode=${SPRING_SQL_INIT_MODE:-never}"
  "--spring.mvc.pathmatch.matching-strategy=${SPRING_MATCHING_STRATEGY:-ant_path_matcher}"
  "--mybatis-plus.mapper-locations=${MYBATIS_MAPPER_LOCATIONS:-classpath*:/mapper/*.xml}"
  "--uploadDir=${UPLOAD_DIR}"
  "--cameraDir=${CAMERA_DIR}"
  "--modelDir=${MODEL_DIR}"
  "--proj-confs.self-report-url=${SELF_REPORT_URL}"
)

if [[ -n "${SPRING_DATASOURCE_DRIVER_CLASS_NAME:-}" ]]; then
  JAVA_ARGS+=("--spring.datasource.driver-class-name=${SPRING_DATASOURCE_DRIVER_CLASS_NAME}")
fi
if [[ -n "${SPRING_DATASOURCE_URL:-}" ]]; then
  JAVA_ARGS+=("--spring.datasource.url=${SPRING_DATASOURCE_URL}")
fi
if [[ -n "${SPRING_DATASOURCE_USERNAME:-}" ]]; then
  JAVA_ARGS+=("--spring.datasource.username=${SPRING_DATASOURCE_USERNAME}")
fi
if [[ -n "${SPRING_DATASOURCE_PASSWORD:-}" ]]; then
  JAVA_ARGS+=("--spring.datasource.password=${SPRING_DATASOURCE_PASSWORD}")
fi
if [[ -n "${JAVA_APP_EXTRA_ARGS:-}" ]]; then
  # shellcheck disable=SC2206
  EXTRA_ARGS=( ${JAVA_APP_EXTRA_ARGS} )
  JAVA_ARGS+=("${EXTRA_ARGS[@]}")
fi

exec "${JAVA_BIN}" -jar "${APP_JAR}" "${JAVA_ARGS[@]}" >>"${LOG_FILE}" 2>&1