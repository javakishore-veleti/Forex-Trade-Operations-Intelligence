#!/usr/bin/env bash
# ============================================================================
# import-workflows.sh — Idempotent import of all n8n workflow JSONs
# ============================================================================
# Imports all workflow JSON files from Agents/workflows/ into the local
# n8n instance via REST API. Safe to re-run (uses upsert logic).
# ============================================================================
set -euo pipefail

N8N_BASE_URL="${N8N_BASE_URL:-http://localhost:5678}"
N8N_API_KEY="${N8N_API_KEY:-}"
N8N_USER="${N8N_USER:-fxops}"
N8N_PASSWORD="${N8N_PASSWORD:-fxops_local_dev}"
WORKFLOWS_DIR="${WORKFLOWS_DIR:-$(cd "$(dirname "$0")/../../../Agents/workflows" && pwd)}"

echo "=== n8n Workflow Import ==="
echo "n8n URL: ${N8N_BASE_URL}"
echo "Workflows dir: ${WORKFLOWS_DIR}"

# Wait for n8n to be ready
echo "Waiting for n8n to be healthy..."
for i in $(seq 1 30); do
    if curl -s "${N8N_BASE_URL}/healthz" >/dev/null 2>&1; then
        echo "n8n is ready."
        break
    fi
    if [ "$i" -eq 30 ]; then
        echo "ERROR: n8n did not become healthy within 30 seconds."
        exit 1
    fi
    sleep 1
done

# Build auth header
if [ -n "${N8N_API_KEY}" ]; then
    AUTH_HEADER="X-N8N-API-KEY: ${N8N_API_KEY}"
else
    AUTH_HEADER="Authorization: Basic $(echo -n "${N8N_USER}:${N8N_PASSWORD}" | base64)"
fi

# Find and import all workflow JSON files
IMPORT_COUNT=0
SKIP_COUNT=0

find "${WORKFLOWS_DIR}" -name "*.json" -type f | sort | while read -r workflow_file; do
    workflow_name=$(basename "${workflow_file}" .json)
    echo "  Importing: ${workflow_name}"

    # Check if workflow already exists (by name)
    existing=$(curl -s -H "${AUTH_HEADER}" \
        "${N8N_BASE_URL}/api/v1/workflows" 2>/dev/null | \
        grep -o "\"name\":\"${workflow_name}\"" || true)

    if [ -n "${existing}" ]; then
        echo "    → Already exists, skipping (idempotent)"
        SKIP_COUNT=$((SKIP_COUNT + 1))
    else
        # Import the workflow
        http_code=$(curl -s -o /dev/null -w "%{http_code}" \
            -X POST \
            -H "${AUTH_HEADER}" \
            -H "Content-Type: application/json" \
            -d @"${workflow_file}" \
            "${N8N_BASE_URL}/api/v1/workflows" 2>/dev/null || echo "000")

        if [ "${http_code}" = "200" ] || [ "${http_code}" = "201" ]; then
            echo "    → Imported successfully"
            IMPORT_COUNT=$((IMPORT_COUNT + 1))
        else
            echo "    → WARNING: Import returned HTTP ${http_code}"
        fi
    fi
done

echo ""
echo "=== Import Complete ==="
echo "Imported: ${IMPORT_COUNT:-0} | Skipped: ${SKIP_COUNT:-0}"
