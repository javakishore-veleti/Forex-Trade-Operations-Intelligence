#!/usr/bin/env bash
# portals-stop-all.sh — Stop all locally started Angular portals.
# Usage: ./portals-stop-all.sh
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
# shellcheck source=./_common.sh
source "$SCRIPT_DIR/_common.sh"

PID_DIR="$RUN_ROOT/portals"
PORTS=(4200 4201 4202)

if [ -d "$PID_DIR" ]; then
  for pid_file in "$PID_DIR"/*.pid; do
    [ -f "$pid_file" ] || continue
    name="$(basename "$pid_file" .pid)"
    pid="$(cat "$pid_file" 2>/dev/null || true)"
    if is_pid_running "$pid"; then
      echo "Stopping $name (pid $pid)..."
      kill_pid_tree "$pid"
    else
      echo "Not running: $name"
    fi
    rm -f "$pid_file"
  done
fi

if command -v lsof >/dev/null 2>&1; then
  for port in "${PORTS[@]}"; do
    pids="$(lsof -nP -iTCP:"$port" -sTCP:LISTEN -t 2>/dev/null || true)"
    if [ -n "$pids" ]; then
      echo "Freeing port $port (pids: $pids)..."
      # shellcheck disable=SC2086
      kill $pids 2>/dev/null || true
    fi
  done
fi

echo "All portal stop attempts complete."
