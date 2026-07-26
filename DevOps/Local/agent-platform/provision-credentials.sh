#!/usr/bin/env bash
# ============================================================================
# provision-credentials.sh — Creates MCP client credentials in n8n
# ============================================================================
# Provisions HTTP Request credentials for each MCP server endpoint using
# the container-internal service names defined in docker-compose.yaml.
# ============================================================================
set -euo pipefail

N8N_BASE_URL="${N8N_BASE_URL:-http://localhost:5678}"
N8N_API_KEY="${N8N_API_KEY:-}"
N8N_USER="${N8N_USER:-fxops}"
N8N_PASSWORD="${N8N_PASSWORD:-fxops_local_dev}"

echo "=== Provisioning MCP Client Credentials ==="

# Wait for n8n to be ready
for i in $(seq 1 30); do
    if curl -s "${N8N_BASE_URL}/healthz" >/dev/null 2>&1; then
        break
    fi
    if [ "$i" -eq 30 ]; then
        echo "ERROR: n8n did not become healthy."
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

# MCP service endpoints (container service names)
declare -A MCP_SERVICES=(
    ["trade-lifecycle-mcp"]="http://trade-lifecycle-service:8081"
    ["state-reconciliation-mcp"]="http://state-reconciliation-service:8082"
    ["risk-calculation-mcp"]="http://risk-calculation-service:8083"
    ["eod-processing-mcp"]="http://eod-processing-service:8084"
    ["business-calendar-mcp"]="http://business-calendar-service:8085"
)

for cred_name in "${!MCP_SERVICES[@]}"; do
    base_url="${MCP_SERVICES[$cred_name]}"
    echo "  Creating credential: ${cred_name} → ${base_url}"

    payload=$(cat <<EOF
{
    "name": "${cred_name}",
    "type": "httpHeaderAuth",
    "data": {
        "name": "X-MCP-Client",
        "value": "n8n-supervisor"
    }
}
EOF
)

    http_code=$(curl -s -o /dev/null -w "%{http_code}" \
        -X POST \
        -H "${AUTH_HEADER}" \
        -H "Content-Type: application/json" \
        -d "${payload}" \
        "${N8N_BASE_URL}/api/v1/credentials" 2>/dev/null || echo "000")

    if [ "${http_code}" = "200" ] || [ "${http_code}" = "201" ]; then
        echo "    → Created successfully"
    elif [ "${http_code}" = "409" ]; then
        echo "    → Already exists (idempotent)"
    else
        echo "    → WARNING: Returned HTTP ${http_code}"
    fi
done

echo ""
echo "=== Credential Provisioning Complete ==="
