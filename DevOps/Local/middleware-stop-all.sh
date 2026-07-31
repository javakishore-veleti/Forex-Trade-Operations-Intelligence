#!/usr/bin/env bash
# middleware-stop-all.sh — Stop all locally started middleware services.
# Usage: ./middleware-stop-all.sh
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
# shellcheck source=./_common.sh
source "$SCRIPT_DIR/_common.sh"

PID_DIR="$RUN_ROOT/middleware"
PORTS=(8081 8082 8083 8084 8085 8086 8087)

if [ -d "$PID_DIR" ]; then
  for pid_file in "$PID_DIR"/*.pid; do
    [ -f "$pid_file" ] || continue
    module="$(basename "$pid_file" .pid)"
    pid="$(cat "$pid_file" 2>/dev/null || true)"
    if is_pid_running "$pid"; then
      echo "Stopping $module (pid $pid)..."
      kill_pid_tree "$pid"
    else
      echo "Not running: $module"
    fi
    rm -f "$pid_file"
  done
fi

# Best-effort cleanup for orphaned listeners on known ports.
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

echo "All middleware stop attempts complete."
