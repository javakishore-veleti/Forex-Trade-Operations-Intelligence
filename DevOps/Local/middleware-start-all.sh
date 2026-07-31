#!/usr/bin/env bash
# middleware-start-all.sh — Start all Spring Boot middleware services locally.
# Usage: ./middleware-start-all.sh
#
# Ports (explicit for local orchestration; resolves lifecycle/risk collision):
#   trade-ingest-service           8081
#   risk-calculation-service       8082
#   trade-lifecycle-service        8083
#   eod-processing-service         8084
#   event-sequence-processor       8085
#   business-calendar-service      8086
#   state-reconciliation-service   8087
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
# shellcheck source=./_common.sh
source "$SCRIPT_DIR/_common.sh"

PID_DIR="$RUN_ROOT/middleware"
LOG_DIR="$RUN_ROOT/logs/middleware"
mkdir -p "$PID_DIR" "$LOG_DIR"

if ! command -v mvn >/dev/null 2>&1; then
  echo "ERROR: mvn not found on PATH" >&2
  exit 1
fi

# module:port
SERVICES=(
  "trade-ingest-service:8081"
  "risk-calculation-service:8082"
  "trade-lifecycle-service:8083"
  "eod-processing-service:8084"
  "event-sequence-processor:8085"
  "business-calendar-service:8086"
  "state-reconciliation-service:8087"
)

for entry in "${SERVICES[@]}"; do
  module="${entry%%:*}"
  port="${entry##*:}"
  pid_file="$PID_DIR/$module.pid"
  log_file="$LOG_DIR/$module.log"

  if [ -f "$pid_file" ] && is_pid_running "$(cat "$pid_file")"; then
    echo "Already running: $module (pid $(cat "$pid_file"), port $port)"
    continue
  fi

  echo "Starting $module on :$port ..."
  (
    cd "$REPO_ROOT"
    nohup mvn -f Middleware/pom.xml -pl "$module" -am spring-boot:run \
      -DskipTests \
      -Dspring-boot.run.arguments="--server.port=${port}" \
      >"$log_file" 2>&1 &
    echo $! >"$pid_file"
  )
  echo "  pid $(cat "$pid_file")  log $log_file"
done

echo "All middleware start commands issued. Use: npm run local:middleware:status-all"
echo "Logs: $LOG_DIR"
