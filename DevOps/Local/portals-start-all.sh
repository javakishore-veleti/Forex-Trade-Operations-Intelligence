#!/usr/bin/env bash
# portals-start-all.sh — Start all Angular portals locally.
# Usage: ./portals-start-all.sh
#
# Ports:
#   Admin           4200
#   TraderDesk      4201
#   FXTradeBlotter  4202
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
# shellcheck source=./_common.sh
source "$SCRIPT_DIR/_common.sh"

PID_DIR="$RUN_ROOT/portals"
LOG_DIR="$RUN_ROOT/logs/portals"
mkdir -p "$PID_DIR" "$LOG_DIR"

if ! command -v npm >/dev/null 2>&1; then
  echo "ERROR: npm not found on PATH" >&2
  exit 1
fi

# portal_dir:port
PORTALS=(
  "Admin:4200"
  "TraderDesk:4201"
  "FXTradeBlotter:4202"
)

for entry in "${PORTALS[@]}"; do
  name="${entry%%:*}"
  port="${entry##*:}"
  portal_dir="$REPO_ROOT/Portals/$name"
  pid_file="$PID_DIR/$name.pid"
  log_file="$LOG_DIR/$name.log"

  if [ ! -d "$portal_dir" ]; then
    echo "ERROR: Missing portal directory: $portal_dir" >&2
    exit 1
  fi

  if [ ! -d "$portal_dir/node_modules" ]; then
    echo "Installing dependencies for $name..."
    npm --prefix "$portal_dir" install
  fi

  if [ -f "$pid_file" ] && is_pid_running "$(cat "$pid_file")"; then
    echo "Already running: $name (pid $(cat "$pid_file"), port $port)"
    continue
  fi

  echo "Starting $name on :$port ..."
  (
    cd "$portal_dir"
    nohup npm start -- --port "$port" --host 127.0.0.1 >"$log_file" 2>&1 &
    echo $! >"$pid_file"
  )
  echo "  pid $(cat "$pid_file")  log $log_file"
done

echo "All portal start commands issued. Use: npm run local:portals:status-all"
echo "Logs: $LOG_DIR"
