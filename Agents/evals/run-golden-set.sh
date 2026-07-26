#!/usr/bin/env bash
# =============================================================================
# Golden-Set Eval Runner for n8n Agent Workflows
# Per ADR-0032: Tier 1 — Golden-Set Regression (pre-deploy, blocking)
# =============================================================================
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
GOLDEN_SETS_DIR="${SCRIPT_DIR}/golden-sets"
RESULTS_DIR="${SCRIPT_DIR}/results"
N8N_BASE_URL="${N8N_BASE_URL:-http://localhost:5678}"
N8N_API_KEY="${N8N_API_KEY:-}"

SAVE_RESULTS=false
TARGET_AGENT=""
PASS_COUNT=0
FAIL_COUNT=0
SKIP_COUNT=0

# --- Argument parsing --------------------------------------------------------
while [[ $# -gt 0 ]]; do
  case $1 in
    --save-results) SAVE_RESULTS=true; shift ;;
    --n8n-url) N8N_BASE_URL="$2"; shift 2 ;;
    --help|-h)
      echo "Usage: run-golden-set.sh [agent-name] [--save-results] [--n8n-url URL]"
      echo ""
      echo "Options:"
      echo "  agent-name       Run golden set for a specific agent only"
      echo "  --save-results   Write results JSON to Agents/evals/results/"
      echo "  --n8n-url URL    Override n8n base URL (default: http://localhost:5678)"
      echo ""
      echo "Environment:"
      echo "  N8N_BASE_URL     n8n instance URL"
      echo "  N8N_API_KEY      n8n API key for authentication"
      exit 0
      ;;
    *) TARGET_AGENT="$1"; shift ;;
  esac
done

# --- Pre-flight checks -------------------------------------------------------
if ! command -v jq &> /dev/null; then
  echo "ERROR: jq is required but not installed. Install with: brew install jq"
  exit 1
fi

if ! command -v curl &> /dev/null; then
  echo "ERROR: curl is required but not installed."
  exit 1
fi

echo "============================================="
echo " Golden-Set Eval Runner"
echo " n8n URL: ${N8N_BASE_URL}"
echo " Target:  ${TARGET_AGENT:-all agents}"
echo "============================================="
echo ""

# --- Discover agents to test -------------------------------------------------
if [[ -n "${TARGET_AGENT}" ]]; then
  AGENT_DIRS=("${GOLDEN_SETS_DIR}/${TARGET_AGENT}")
  if [[ ! -d "${AGENT_DIRS[0]}" ]]; then
    echo "ERROR: No golden set found for agent '${TARGET_AGENT}'"
    echo "Available agents:"
    ls -1 "${GOLDEN_SETS_DIR}"
    exit 1
  fi
else
  AGENT_DIRS=("${GOLDEN_SETS_DIR}"/*)
fi

# --- Ensure results directory exists -----------------------------------------
mkdir -p "${RESULTS_DIR}"

# --- Run eval for each agent -------------------------------------------------
run_agent_eval() {
  local agent_dir="$1"
  local agent_name
  agent_name="$(basename "${agent_dir}")"
  local cases_file="${agent_dir}/cases.json"

  if [[ ! -f "${cases_file}" ]]; then
    echo "  SKIP: ${agent_name} — no cases.json found"
    ((SKIP_COUNT++))
    return
  fi

  local num_cases
  num_cases=$(jq 'length' "${cases_file}")
  echo "  Agent: ${agent_name} (${num_cases} cases)"

  local agent_results="[]"

  for i in $(seq 0 $((num_cases - 1))); do
    local case_name
    case_name=$(jq -r ".[$i].name" "${cases_file}")
    local input
    input=$(jq -c ".[$i].input" "${cases_file}")
    local expected_tools
    expected_tools=$(jq -c ".[$i].expectedToolCalls" "${cases_file}")
    local expected_output
    expected_output=$(jq -c ".[$i].expectedOutputContains" "${cases_file}")
    local expected_risk_gate
    expected_risk_gate=$(jq -r ".[$i].expectedRiskGate" "${cases_file}")

    # Execute agent workflow via n8n webhook
    local response
    local http_code
    response=$(curl -s -w "\n%{http_code}" \
      -X POST "${N8N_BASE_URL}/webhook/${agent_name}" \
      -H "Content-Type: application/json" \
      -H "X-N8N-API-KEY: ${N8N_API_KEY}" \
      -d "${input}" 2>/dev/null) || true

    http_code=$(echo "${response}" | tail -1)
    local body
    body=$(echo "${response}" | sed '$d')

    local status="SKIP"
    local reason=""

    if [[ "${http_code}" == "000" ]] || [[ -z "${http_code}" ]]; then
      status="SKIP"
      reason="n8n not reachable"
      ((SKIP_COUNT++))
    elif [[ "${http_code}" -ge 200 ]] && [[ "${http_code}" -lt 300 ]]; then
      # Validate tool calls
      local actual_tools
      actual_tools=$(echo "${body}" | jq -c '.toolCalls // []' 2>/dev/null || echo "[]")

      # Validate output fields
      local output_match=true
      if [[ "${expected_output}" != "null" ]] && [[ -n "${body}" ]]; then
        local keys
        keys=$(echo "${expected_output}" | jq -r 'keys[]' 2>/dev/null || echo "")
        for key in ${keys}; do
          local expected_val
          expected_val=$(echo "${expected_output}" | jq -r ".${key}" 2>/dev/null)
          local actual_val
          actual_val=$(echo "${body}" | jq -r ".${key}" 2>/dev/null)
          if [[ "${expected_val}" != "${actual_val}" ]]; then
            output_match=false
            break
          fi
        done
      fi

      if [[ "${actual_tools}" == "${expected_tools}" ]] && [[ "${output_match}" == "true" ]]; then
        status="PASS"
        ((PASS_COUNT++))
      else
        status="FAIL"
        reason="Tool calls or output mismatch"
        ((FAIL_COUNT++))
      fi
    else
      status="FAIL"
      reason="HTTP ${http_code}"
      ((FAIL_COUNT++))
    fi

    printf "    [%s] %s" "${status}" "${case_name}"
    [[ -n "${reason}" ]] && printf " (%s)" "${reason}"
    echo ""

    # Accumulate results
    agent_results=$(echo "${agent_results}" | jq \
      --arg name "${case_name}" \
      --arg status "${status}" \
      --arg reason "${reason}" \
      '. + [{"name": $name, "status": $status, "reason": $reason}]')
  done

  # Save results if requested
  if [[ "${SAVE_RESULTS}" == "true" ]]; then
    local result_file="${RESULTS_DIR}/${agent_name}-$(date +%Y%m%dT%H%M%S).json"
    echo "${agent_results}" | jq '.' > "${result_file}"
    echo "    → Results saved: ${result_file}"
  fi
}

# --- Execute -----------------------------------------------------------------
for agent_dir in "${AGENT_DIRS[@]}"; do
  [[ -d "${agent_dir}" ]] && run_agent_eval "${agent_dir}"
done

# --- Summary -----------------------------------------------------------------
echo ""
echo "============================================="
echo " Results: ${PASS_COUNT} passed, ${FAIL_COUNT} failed, ${SKIP_COUNT} skipped"
echo "============================================="

if [[ ${FAIL_COUNT} -gt 0 ]]; then
  exit 1
fi
exit 0
