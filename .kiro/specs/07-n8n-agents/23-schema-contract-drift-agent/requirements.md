# Requirements Document — Schema & Contract Drift Agent

> **Technology-agnostic spec.** References **Technology Roles** from
> `01-initial-setup/01-technology-stack`. Contains no product names or versions.
> Inherits `architecture-golden-path/01-service-nfrs` for cross-cutting NFRs.

## Introduction

This feature defines the **Schema & Contract Drift Agent** — a specialized
agent that detects breaking or incompatible schema changes and API contract
drift, then flags affected consumers before they encounter runtime failures.

This agent is implemented as an `AGENT_PLATFORM` workflow export. It does NOT
deploy or rollback schemas — it analyzes compatibility and flags breaking
changes via advisory actions.

All identifiers in examples use the synthetic `FX-` prefix.

---

## Glossary

- **SchemaContractDriftAgent**: The `AGENT_PLATFORM` workflow detecting schema/API drift.
- **SchemaVersion**: A registered schema version in the schema registry.
- **ContractDrift**: A breaking or incompatible change between schema versions.
- **ConsumerRegistry**: The mapping of which services consume which topics/APIs.
- **BreakingChangeFlag**: An advisory action notifying affected service owners.

---

## Requirements

### Requirement 1: Schema Compatibility Check

**User Story:** As a platform engineer, I want new schema versions checked
for compatibility before deployment, so that breaking changes are caught early.

#### Acceptance Criteria

1. THE agent SHALL be triggered when a new schema version is registered or
   an OpenAPI spec is deployed.
2. THE agent SHALL call `getSchemaCompatibility(subject, version)` to check
   backward/forward compatibility.
3. THE agent SHALL detect: removed fields, type changes, enum narrowing,
   required-field additions as breaking changes.
4. THE agent SHALL produce a semantic diff between old and new versions.

---

### Requirement 2: Consumer Impact Analysis

**User Story:** As a service owner, I want to know which consumers will break
from a schema change, so that I can coordinate the migration.

#### Acceptance Criteria

1. THE agent SHALL call `findConsumersOf(topic)` to retrieve downstream
   consumers of the changed schema.
2. THE agent SHALL call `simulatePayloadAgainstConsumers()` to test
   consumer deserialization with the new schema.
3. THE agent SHALL identify which business flows degrade: settlement,
   risk calculation, reporting, etc.
4. THE agent SHALL rank consumers by criticality and failure severity.

---

### Requirement 3: Breaking-Change Flag

**User Story:** As a release manager, I want breaking changes flagged before
deployment proceeds, so that I can decide to block or coordinate the rollout.

#### Acceptance Criteria

1. WHEN a breaking change is detected, THE agent SHALL call
   `flagBreakingChange()` with affected consumers, severity, and impact.
2. THE flag SHALL be presented at a HITL gate for review before any
   deployment gate proceeds.
3. THE agent SHALL include rollback guidance (prior version reference).
4. THE agent SHALL log all schema drift events for trend analysis.

---

## Risk Classification

- **Inherent risk:** M (advisory flag blocks/delays deployment pipeline)
- **HITL requirement:** Required for breaking-change flag

---

## Model Tiers

| Role | Tier | Usage |
|------|------|-------|
| Perception | Lightweight (Haiku-class) | Semantic diff between schema versions |
| Detection | Deterministic (rules) | Compatibility rule evaluation |
| Reasoning | Deep (Opus-class) | Consumer impact analysis, business flow mapping |
| Policy | Deterministic | Compatibility enforcement rules |
| Memory | Episodic | Prior drift events and outcomes |

---

## MCP Tools Called

| Tool | Source Service | Risk | Purpose |
|------|---------------|------|---------|
| `getSchemaCompatibility(subject, ver)` | schema-registry-mcp | L | Compatibility check |
| `findConsumersOf(topic)` | schema-registry-mcp | L | Downstream consumer list |
| `simulatePayloadAgainstConsumers()` | schema-registry-mcp | L | Deserialization test |
| `flagBreakingChange()` | schema-registry-mcp | M | Advisory flag (gated) |

---

## Domain Acceptance Scenarios (Golden-Set Eval Harness)

| Scenario ID | Input | Expected Behavior |
|---|---|---|
| SCHEMA-EVAL-01 | New version removes field | Breaking detected, consumers listed |
| SCHEMA-EVAL-02 | Backward-compatible addition | Compatible, no flag |
| SCHEMA-EVAL-03 | Enum narrowing | Breaking, affected flows identified |
| SCHEMA-EVAL-04 | Flag approved | Calls flagBreakingChange |
| SCHEMA-EVAL-05 | OpenAPI breaking change | Identified, affected services listed |
| SCHEMA-EVAL-06 | No consumers for topic | "No downstream impact detected" |

---

## Agentic Design Patterns

| Pattern | Application |
|---|---|
| 5 — Tool Use | MCP calls to schema registry service |
| 12 — Human-in-the-Loop | Breaking-change flag gate |
| 14 — Inter-Agent | Notify dependent service agents |
| 18 — Guardrails | Prevent breaking deployment |

---

## Python Sidecar Dependency

- **schema-semantic-diff**: Produces semantic diff between schema versions,
  classifying changes by category (field removal, type change, enum change,
  default change). NOT a trigger — called on demand.
