#!/usr/bin/env bash
# commit-specs.sh — manually commit all pending spec changes with a single command
# Usage: ./scripts/commit-specs.sh "optional message override"

set -euo pipefail

REPO_ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$REPO_ROOT"

MSG="${1:-feat(specs): update spec documents}"

git add .kiro/specs/
CHANGED=$(git diff --cached --name-only)

if [ -z "$CHANGED" ]; then
  echo "Nothing to commit in .kiro/specs/"
  exit 0
fi

echo "Committing:"
echo "$CHANGED"
git commit -m "$MSG"
echo "Done."
