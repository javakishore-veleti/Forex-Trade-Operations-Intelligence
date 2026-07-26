# ADR-0018: Agent Permitted-Action Catalogue

**Status:** Accepted

**Date:** 2024-02-16

## Context

Each agent must have a bounded set of actions it can perform. Unbounded agents risk executing unintended operations, especially when LLM reasoning leads to unexpected tool-call sequences. The catalogue of permitted actions must be explicit, versioned, and enforceable.

Three approaches were evaluated:

1. **Fixed enum** — a hardcoded Java enum listing every permitted action per agent type.
2. **Configurable policy** — a YAML/JSON policy file per agent defining permitted tools, parameters, and constraints.
3. **LLM-generated** — the LLM decides what actions are appropriate based on its system prompt.

## Decision

We adopt **configurable policy files** that define each agent's permitted-action catalogue, enforced by the MCP server at runtime.

### Implementation

- Each agent has a policy file: `Agents/policies/{agent-name}.policy.json`
- Policy structure:
  ```json
  {
    "agent": "dlq-triage-agent",
    "version": "1.2",
    "permitted_tools": [
      {"tool": "get-dlq-messages", "risk": "L", "max_per_execution": 100},
      {"tool": "classify-dlq-error", "risk": "L", "max_per_execution": 50},
      {"tool": "replay-dlq-messages", "risk": "M", "max_per_execution": 1, "requires_approval": true},
      {"tool": "purge-dlq-messages", "risk": "H", "max_per_execution": 1, "requires_approval": true}
    ],
    "denied_tools": ["*"],
    "max_tool_calls_per_execution": 25,
    "timeout_seconds": 300
  }
  ```
- The MCP server loads policies at startup and validates every tool call against the invoking agent's policy.
- Calls to tools not in `permitted_tools` are rejected with `403 ACTION_NOT_PERMITTED`.

### Example

Trade Lifecycle Reconstruction Agent tries to call `purge-dlq-messages`:
→ MCP server checks policy → tool not in permitted list → `403 ACTION_NOT_PERMITTED`
→ Agent receives error → reports to supervisor that action is outside its scope.

## Alternatives Considered

| Alternative | Reason Rejected |
|-------------|-----------------|
| Fixed enum | Too rigid; requires Java recompilation for policy changes; cannot be updated by ops team |
| LLM-generated actions | Fundamentally unsafe — LLM cannot be trusted to self-restrict; violates principle of least privilege |
| No catalogue (rely on prompt) | Prompt injection or hallucination could lead to unauthorized actions; no enforcement layer |

## Consequences

### Positive
- Defense-in-depth: even if LLM hallucinates a tool call, MCP server blocks unauthorized actions
- Ops-manageable: policy files can be updated and hot-reloaded without code deployment
- Auditable: policy version is logged with every tool invocation
- Rate limiting per tool prevents runaway agent loops

### Negative
- Policy file maintenance overhead — must be updated when agents gain new capabilities
- Hot-reload adds configuration drift risk if not version-controlled
- Overly restrictive policies could block valid agent actions

### Mitigations
- Policy files are version-controlled in Git; PRs required for changes
- CI validates policy files against MCP tool registry (no references to non-existent tools)
- Agent evaluation golden sets detect when valid actions are being blocked
