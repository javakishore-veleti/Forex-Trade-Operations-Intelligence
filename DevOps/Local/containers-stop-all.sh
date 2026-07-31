#!/usr/bin/env bash
# containers-stop-all.sh — Stop all local Docker infrastructure roles (reverse order).
# Usage: ./containers-stop-all.sh
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
# shellcheck source=./_common.sh
source "$SCRIPT_DIR/_common.sh"

ROLES=(
  observability-metrics
  observability-logging
  observability-tracing
  agent-platform
  event-stream
  vector-store
  graph-store
  document-store
  cache
  relational-store
)

for role in "${ROLES[@]}"; do
  role_dir="$SCRIPT_DIR/$role"
  if [ ! -d "$role_dir" ]; then
    echo "ERROR: Missing directory: $role_dir" >&2
    exit 1
  fi
  compose_file="$(compose_file_for "$role_dir")"
  echo "Stopping $role..."
  docker compose -f "$compose_file" down
done

echo "All local containers stopped."
