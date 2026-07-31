#!/usr/bin/env bash
# middleware-status-all.sh — Show status of local middleware services.
# Usage: ./middleware-status-all.sh
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
# shellcheck source=./_common.sh
source "$SCRIPT_DIR/_common.sh"

PID_DIR="$RUN_ROOT/middleware"

SERVICES=(
  "trade-ingest-service:8081"
  "risk-calculation-service:8082"
  "trade-lifecycle-service:8083"
  "eod-processing-service:8084"
  "event-sequence-processor:8085"
  "business-calendar-service:8086"
  "state-reconciliation-service:8087"
)

printf "%-32s %-8s %-10s %s\n" "SERVICE" "PORT" "PROCESS" "HTTP"
printf "%-32s %-8s %-10s %s\n" "-------" "----" "-------" "----"

for entry in "${SERVICES[@]}"; do
  module="${entry%%:*}"
  port="${entry##*:}"
  pid_file="$PID_DIR/$module.pid"
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
      code="$(curl -s -o /dev/null -w '%{http_code}' --max-time 2 "http://127.0.0.1:${port}/actuator/health" || true)"
      if [ "$code" = "200" ]; then
        http="healthy"
      elif [ -n "$code" ] && [ "$code" != "000" ]; then
        http="http:$code"
      fi
    fi
  fi

  printf "%-32s %-8s %-10s %s\n" "$module" "$port" "$process" "$http"
done
