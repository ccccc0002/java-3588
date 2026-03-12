#!/usr/bin/env bash
set -euo pipefail

if command -v tmux >/dev/null 2>&1; then
  echo "OK: tmux already installed: $(tmux -V)"
  exit 0
fi

echo "INFO: tmux not found, installing..."

if command -v apt-get >/dev/null 2>&1; then
  sudo apt-get update
  sudo apt-get install -y tmux
elif command -v dnf >/dev/null 2>&1; then
  sudo dnf install -y tmux
elif command -v yum >/dev/null 2>&1; then
  sudo yum install -y tmux
elif command -v opkg >/dev/null 2>&1; then
  sudo opkg update
  sudo opkg install tmux
else
  echo "ERROR: unsupported package manager, install tmux manually." >&2
  exit 1
fi

echo "OK: tmux installed: $(tmux -V)"
