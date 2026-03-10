#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
REPO_ROOT="$(cd "${SCRIPT_DIR}/../.." && pwd)"
MODE="${1:-build}"
shift || true

python3 "${REPO_ROOT}/scripts/rk3588/ffmpeg_rockchip_install.py" "${MODE}" --repo-root "${REPO_ROOT}" --script-only "$@" | bash
