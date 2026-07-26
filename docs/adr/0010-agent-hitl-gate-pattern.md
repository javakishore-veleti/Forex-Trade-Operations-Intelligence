# ADR-0010: Agent Human-in-the-Loop (HITL) Gate Pattern

**Status:** Accepted

**Date:** 2024-02-10

## Context

All Medium and High risk agent actions require human approval before execution (architectural constraint). The platform needs a mechanism to pause workflow execution, present an approval request to an operator, and resume only upon explicit approval.

Three patterns were evaluated:

1. **n8n Wait node** — native n8n mechanism that pauses execution and resumes via webhook callback.
2. **External approval service** — a dedicated Spring Boot microservice managing approval queues with REST APIs.
3. **Slack/Teams integration** — approval requests sent as interactive messages; button clicks resume the workflow.

## Decision

We adopt the **n8n Wait node** as the primary HITL gate, with the Admin Portal providing the approval UI that calls back into n8n's resume webhook.

### Implementation

- Agent workflows insert a Wait node before any M/H-risk action.
- The Wait node emits an approval request event to Kafka topic `fx.agent.approval-requests`.
- The Admin Portal's HITL Approval Queue (Angular component) consumes these events and renders them with context (impact report, proposed action, affected trades).
- Operator clicks Approve/Reject → Admin Portal calls n8n's webhook resume URL with the decision.
- Wait node timeout: 4 hours (configurable). Expired approvals auto-reject.

### Example

DLQ Triage Agent proposes replaying 150 messages for trade batch FX-BATCH-0088:
1. Agent generates impact report → Wait node pauses
2. Admin Portal shows: "Replay 150 msgs, estimated 2min processing, affects 12 counterparties"
3. Operator approves → n8n resumes → agent executes replay

## Alternatives Considered

| Alternative | Reason Rejected |
|-------------|-----------------|
| External approval service | Adds a new microservice with its own persistence; duplicates what n8n Wait provides natively |
| Slack/Teams integration | Not suitable for regulated environment; audit trail harder to enforce; not all operators use chat tools |
| Email-based approval | Too slow for time-sensitive FX operations; poor UX |

## Consequences

### Positive
- Zero additional infrastructure — uses n8n's built-in capability
- Full audit trail via n8n execution logs + Kafka event history
- Admin Portal provides rich context alongside approval buttons
- Timeout prevents indefinite workflow stalls

### Negative
- n8n Wait node stores state in n8n's execution DB — scaling depends on n8n deployment
- If n8n restarts during wait, execution must recover (n8n supports this but adds recovery complexity)
- Single approval channel (Admin Portal) — no mobile/chat fallback

### Mitigations
- n8n deployed with PostgreSQL execution store for crash recovery
- Approval request events also stored in `approval_audit` table for compliance
- Future: optional Slack notification (inform-only) that links to Admin Portal for action
