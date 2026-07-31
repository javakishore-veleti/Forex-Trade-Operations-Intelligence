#!/usr/bin/env bash
# portals-status-all.sh — Show status of local Angular portals.
# Usage: ./portals-status-all.sh
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
# shellcheck source=./_common.sh
source "$SCRIPT_DIR/_common.sh"

PID_DIR="$RUN_ROOT/portals"

PORTALS=(
  "Admin:4200"
  "TraderDesk:4201"
  "FXTradeBlotter:4202"
)

printf "%-20s %-8s %-10s %s\n" "PORTAL" "PORT" "PROCESS" "HTTP"
printf "%-20s %-8s %-10s %s\n" "------" "----" "-------" "----"

for entry in "${PORTALS[@]}"; do
  name="${entry%%:*}"
  port="${entry##*:}"
  pid_file="$PID_DIR/$name.pid"
  process="down"
  http="-"

  if [ -f "$pid_file" ]; then
    pid="$(cat "$pid_file")"
    if is_pid_running "$pid"; then
      process="up($pid)"
    else
      process="stale"
    fi
  fi

  if port_listening "$port"; then
    http="listen"
    if command -v curl >/dev/null 2>&1; then
      code="$(curl -s -o /dev/null -w '%{http_code}' --max-time 2 "http://127.0.0.1:${port}/" || true)"
      if [ "$code" = "200" ] || [ "$code" = "304" ]; then
        http="ready"
      elif [ -n "$code" ] && [ "$code" != "000" ]; then
        http="http:$code"
      fi
    fi
  fi

  printf "%-20s %-8s %-10s %s\n" "$name" "$port" "$process" "$http"
done
