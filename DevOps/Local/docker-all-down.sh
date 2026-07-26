#!/usr/bin/env bash
# docker-all-down.sh — Stops all local infrastructure in reverse dependency order.
# Usage: ./docker-all-down.sh
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"

if [ ! -d "$SCRIPT_DIR" ]; then
  echo "ERROR: Script directory not found: $SCRIPT_DIR" >&2
  exit 1
fi

# Reverse dependency order: observability first, then platform, then stream, then stores
ROLES=(
  observability-metrics
  observability-logging
  agent-platform
  event-stream
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
  echo "Stopping $role..."
  docker compose -f "$role_dir/docker-compose.yml" down
done

echo "All services stopped."
