# ADR-0014: Agent Risk Classification Enforcement

**Status:** Accepted

**Date:** 2024-02-14

## Context

Every agent action is classified as Low (L), Medium (M), or High (H) risk. M and H actions require HITL approval. The enforcement mechanism must be tamper-resistant — an agent workflow bug or LLM hallucination must not bypass the gate.

Three enforcement approaches were evaluated:

1. **Annotation-based gating** — risk level is metadata on each MCP tool; the MCP server enforces gates.
2. **Policy service** — a dedicated OPA/Drools service evaluates each action request against policy rules.
3. **Workflow-level check** — each n8n workflow includes an IF node checking risk level before execution.

## Decision

We adopt **annotation-based gating at the MCP server layer**, with the Drools rule engine as the policy backend for complex scenarios.

### Implementation

- Each MCP tool definition includes a `riskLevel` field: `L`, `M`, or `H`.
- The MCP server intercepts every tool invocation and checks the risk level.
- For `L` tools: execute immediately, log the call.
- For `M`/`H` tools: verify that a valid approval token (from the HITL gate) is present in the request headers. If absent, reject with `403 APPROVAL_REQUIRED`.
- Complex risk assessments (e.g., "replay is L for < 10 messages, M for 10-100, H for > 100") are evaluated by Drools rules loaded at startup.

### Example

DLQ Triage Agent wants to replay 150 messages:
1. Agent calls MCP tool `replay-dlq-messages` with `count: 150`
2. MCP server evaluates Drools rule: count > 100 → `H` risk
3. MCP server returns `403 APPROVAL_REQUIRED` with risk explanation
4. Agent workflow hits Wait node → HITL approval obtained → retries with approval token
5. MCP server validates token → executes replay

## Alternatives Considered

| Alternative | Reason Rejected |
|-------------|-----------------|
| Workflow-level IF check | Bypassable — a workflow bug could skip the check; not defense-in-depth |
| Dedicated policy service (OPA) | Adds network hop and new infrastructure; Drools already in platform for business rules |
| LLM self-assessment | Never — LLM cannot reliably assess its own action risk; violates determinism boundary |

## Consequences

### Positive
- Server-side enforcement — agents cannot bypass regardless of workflow logic errors
- Defense-in-depth: even if n8n workflow is misconfigured, MCP server blocks unauthorized actions
- Drools rules enable dynamic risk thresholds without code changes
- Full audit trail of every gating decision in MCP server logs

### Negative
- Drools rule complexity could grow; requires governance process for rule changes
- Approval token validation adds latency to M/H tool calls (~10ms for token verification)
- Risk level assignment for new tools requires explicit decision during tool registration

### Mitigations
- Drools rules are version-controlled in `Middleware/shared-domain-contracts`
- Token verification uses Redis-cached approval records — sub-millisecond lookup
- Tool registration checklist includes mandatory risk level classification with justification
