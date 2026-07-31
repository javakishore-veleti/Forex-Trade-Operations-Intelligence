#!/usr/bin/env bash
# Shared helpers for local orchestration scripts.
# shellcheck disable=SC2034

set -euo pipefail

LOCAL_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
REPO_ROOT="$(cd "$LOCAL_DIR/../.." && pwd)"
RUN_ROOT="$REPO_ROOT/.run"

compose_file_for() {
  local role_dir="$1"
  if [ -f "$role_dir/docker-compose.yml" ]; then
    echo "$role_dir/docker-compose.yml"
  elif [ -f "$role_dir/docker-compose.yaml" ]; then
    echo "$role_dir/docker-compose.yaml"
  else
    echo "ERROR: No docker-compose.yml/.yaml in $role_dir" >&2
    return 1
  fi
}

is_pid_running() {
  local pid="$1"
  [ -n "$pid" ] && kill -0 "$pid" 2>/dev/null
}

port_listening() {
  local port="$1"
  if command -v lsof >/dev/null 2>&1; then
    lsof -nP -iTCP:"$port" -sTCP:LISTEN >/dev/null 2>&1
  elif command -v nc >/dev/null 2>&1; then
    nc -z 127.0.0.1 "$port" >/dev/null 2>&1
  else
    return 1
  fi
}

kill_pid_tree() {
  local pid="$1"
  if ! is_pid_running "$pid"; then
    return 0
  fi
  # Kill children first (mvn/ng wrappers spawn JVMs / node children).
  if command -v pkill >/dev/null 2>&1; then
    pkill -P "$pid" 2>/dev/null || true
  fi
  kill "$pid" 2>/dev/null || true
  sleep 1
  if is_pid_running "$pid"; then
    kill -9 "$pid" 2>/dev/null || true
  fi
}
