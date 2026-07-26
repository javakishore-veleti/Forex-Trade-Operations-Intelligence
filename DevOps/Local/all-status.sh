#!/usr/bin/env bash
# all-status.sh — Shows status of all local infrastructure containers.
# Usage: ./all-status.sh
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"

if [ ! -d "$SCRIPT_DIR" ]; then
  echo "ERROR: Script directory not found: $SCRIPT_DIR" >&2
  exit 1
fi

ROLES=(
  relational-store
  cache
  document-store
  graph-store
  event-stream
  agent-platform
  observability-logging
  observability-metrics
)

for role in "${ROLES[@]}"; do
  role_dir="$SCRIPT_DIR/$role"
  if [ ! -d "$role_dir" ]; then
    echo "ERROR: Missing directory: $role_dir" >&2
    exit 1
  fi
  echo "=== $role ==="
  docker compose -f "$role_dir/docker-compose.yml" ps
  echo ""
done
