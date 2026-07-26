# Requirements Document — Adaptive Transaction Routing Agent

> **Technology-agnostic spec.** References **Technology Roles** from
> `01-initial-setup/01-technology-stack`. Contains no product names or versions.
> Inherits `architecture-golden-path/01-service-nfrs` for cross-cutting NFRs.

## Introduction

This feature defines the **Adaptive Transaction Routing Agent** — a specialized
agent that proposes temporary routing policy changes when service degradation
or capacity constraints are detected. The agent evaluates runtime conditions
and proposes routing configurations validated by a deterministic rules service.

This agent is implemented as an `AGENT_PLATFORM` workflow export. It does NOT
route individual trades — it proposes policy changes to the routing
configuration via human-gated tools.

All identifiers in examples use the synthetic `FX-` prefix.

---

## Glossary

- **AdaptiveRoutingAgent**: The `AGENT_PLATFORM` workflow proposing routing policies.
- **RoutingPolicy**: A configuration defining how trades are routed based on region, pair, counterparty, capacity, or health.
- **RuntimeCondition**: A snapshot of service health, capacity, and cutoff proximity affecting routing decisions.
- **PolicyValidation**: Deterministic rules-service check that the proposed policy is safe and consistent.
- **PolicyApplication**: The gated action that applies a validated routing config.

---

## Requirements

### Requirement 1: Runtime Condition Assessment

**User Story:** As an operations lead, I want routing conditions assessed
continuously, so that degraded services get traffic relief before failures.

#### Acceptance Criteria

1. THE agent SHALL call `getRuntimeConditions()` to assess: service health,
   downstream capacity, cutoff proximity, market availability.
2. THE agent SHALL identify services at risk of degradation from current load.
3. THE agent SHALL factor in region, currency pair, and counterparty when
   assessing conditions.
4. THE agent SHALL NOT be triggered per-trade (batch/periodic assessment only).

---

### Requirement 2: Routing Policy Proposal

**User Story:** As an infrastructure engineer, I want routing policy changes
proposed when conditions warrant, so that I can approve and apply them.

#### Acceptance Criteria

1. WHEN conditions warrant routing adjustment, THE agent SHALL call
   `proposeRoutingPolicy()` to generate a candidate policy.
2. THE agent SHALL call `validateRoutingPolicy()` to verify the proposal
   against deterministic rules (no orphaned trades, no limit violations).
3. THE proposal SHALL include: current policy, proposed changes, affected
   trade flow, expected improvement.
4. THE proposal SHALL be presented at a HITL gate.

---

### Requirement 3: Policy Application and Observation

**User Story:** As an approver, I want the policy applied after my approval
and its effect observed, so that I can confirm or rollback.

#### Acceptance Criteria

1. WHEN approved, THE agent SHALL call `applyRoutingConfig()` to activate
   the new policy.
2. THE agent SHALL note the policy as temporary with automatic expiry
   (configurable, default: 30 minutes).
3. THE agent SHALL log the complete policy lifecycle for audit.

---

## Risk Classification

- **Inherent risk:** H (changes routing configuration affecting trade flow)
- **HITL requirement:** Mandatory for policy application

---

## Model Tiers

| Role | Tier | Usage |
|------|------|-------|
| Reasoning | Deep (Opus-class) | Condition analysis, policy design |
| Policy | Deterministic (rules service) | Validation of proposed policy |
| Execution | Deep (Opus-class) | Application orchestration |
| Memory | Episodic | Prior routing events and outcomes |

---

## MCP Tools Called

| Tool | Source Service | Risk | Purpose |
|------|---------------|------|---------|
| `getRuntimeConditions()` | observability-mcp | L | Service health, capacity, cutoffs |
| `proposeRoutingPolicy()` | routing-mcp | L | Generate candidate policy |
| `validateRoutingPolicy()` | routing-mcp | L | Deterministic rules validation |
| `applyRoutingConfig()` | routing-mcp | H | Apply policy (gated) |

---

## Domain Acceptance Scenarios (Golden-Set Eval Harness)

| Scenario ID | Input | Expected Behavior |
|---|---|---|
| ROUTE-EVAL-01 | Enrichment-svc degraded | Re-route EUR pairs to backup |
| ROUTE-EVAL-02 | Policy approved | Calls applyRoutingConfig |
| ROUTE-EVAL-03 | Invalid policy (orphans trades) | Validation rejects; revise |
| ROUTE-EVAL-04 | Multiple services degraded | Composite policy proposal |
| ROUTE-EVAL-05 | All healthy | "No routing adjustment needed" |
| ROUTE-EVAL-06 | Policy denied | Holds; records decision |

---

## Agentic Design Patterns

| Pattern | Application |
|---|---|
| 5 — Tool Use | MCP calls to routing and observability services |
| 6 — Planning | Policy design and alternative evaluation |
| 12 — Human-in-the-Loop | Policy application gate |
| 15 — Resource-Aware | Capacity-aware routing decisions |

---

## Python Sidecar Dependency

- None — condition assessment uses deterministic service metrics. Routing
  decisions are rules-validated, not ML-based.
