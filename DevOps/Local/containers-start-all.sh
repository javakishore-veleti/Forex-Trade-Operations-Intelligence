#!/usr/bin/env bash
# containers-start-all.sh — Start all local Docker infrastructure roles.
# Usage: ./containers-start-all.sh
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
# shellcheck source=./_common.sh
source "$SCRIPT_DIR/_common.sh"

# Dependency order: stores → stream → platform → observability
ROLES=(
  relational-store
  cache
  document-store
  graph-store
  vector-store
  event-stream
  agent-platform
  observability-tracing
  observability-logging
  observability-metrics
)

for role in "${ROLES[@]}"; do
  role_dir="$SCRIPT_DIR/$role"
  if [ ! -d "$role_dir" ]; then
    echo "ERROR: Missing directory: $role_dir" >&2
    exit 1
  fi
  compose_file="$(compose_file_for "$role_dir")"
  echo "Starting $role..."
  docker compose -f "$compose_file" up -d
done

echo "All local containers started."
