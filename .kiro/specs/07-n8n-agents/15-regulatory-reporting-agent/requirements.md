# Requirements Document — Regulatory Reporting Completeness Agent

> **Technology-agnostic spec.** References **Technology Roles** from
> `01-initial-setup/01-technology-stack`. Contains no product names or versions.
> Inherits `architecture-golden-path/01-service-nfrs` for cross-cutting NFRs.

## Introduction

This feature defines the **Regulatory Reporting Completeness Agent** — a
specialized agent that verifies all reportable trades have been submitted to
regulatory reporting systems before the reporting deadline. It identifies
gaps between the reportable universe and actual submissions, explains why
gaps exist, and gates resubmission behind human approval.

This agent is implemented as an `AGENT_PLATFORM` workflow export. It uses
only synthetic regulatory regimes — no real regulatory body data.

All identifiers in examples use the synthetic `FX-` prefix.

---

## Glossary

- **RegulatoryReportingAgent**: The `AGENT_PLATFORM` workflow verifying reporting completeness.
- **ReportableUniverse**: The set of trades that must be reported under a given regime.
- **SubmittedReports**: Trades already submitted to the reporting system.
- **ReportingGap**: A trade in the universe that has not been submitted.
- **CompletenessAttestation**: A signed-off statement that all required reports are submitted.

---

## Requirements

### Requirement 1: Completeness Verification

**User Story:** As a compliance officer, I want all reportable trades verified
against submissions before the deadline, so that I can certify completeness.

#### Acceptance Criteria

1. THE agent SHALL retrieve the reportable universe via `getReportableUniverse()`.
2. THE agent SHALL retrieve submitted reports via `getSubmittedReports()`.
3. THE agent SHALL compute the diff via `findReportingGaps()` identifying
   un-submitted trades.
4. THE agent SHALL check for field-level validation failures via
   `getFieldValidationFailures()`.
5. THE agent SHALL produce a completeness percentage and gap list.

---

### Requirement 2: Gap Explanation

**User Story:** As a reporting analyst, I want to understand why each gap
exists, so that I can determine if it's a system error or legitimate exclusion.

#### Acceptance Criteria

1. THE agent SHALL use the `ReasoningModel` to explain each gap: late
   capture, validation failure, system error, or intentional exclusion.
2. THE agent SHALL categorize gaps by reason and priority.
3. THE agent SHALL identify systemic patterns (e.g., "all trades from
   branch FX-BR-003 missing due to feed delay").

---

### Requirement 3: Resubmission Gate

**User Story:** As a compliance manager, I want resubmission of missing
reports to require my approval, so that corrections are controlled.

#### Acceptance Criteria

1. WHEN gaps are identified and correctable, THE agent SHALL propose
   resubmission for human approval.
2. THE HITL gate SHALL include: trade IDs, gap reason, resubmission payload.
3. WHEN approved, THE agent SHALL call `resubmitReport()`.
4. WHEN denied, THE gap remains documented for manual handling.

---

## Risk Classification

- **Inherent risk:** M (resubmission modifies regulatory reports)
- **HITL requirement:** Mandatory for resubmission

---

## Model Tiers

| Role | Tier | Usage |
|------|------|-------|
| Detection | Deterministic | Universe vs submitted diff |
| Reasoning | Deep (Opus-class) | Gap explanation, pattern identification |
| Policy | Deterministic | Reportability rules |
| Memory | Episodic | Prior gap patterns |

---

## MCP Tools Called

| Tool | Source Service | Risk | Purpose |
|------|---------------|------|---------|
| `getReportableUniverse()` | reporting-mcp | L | Full reportable set |
| `getSubmittedReports()` | reporting-mcp | L | Already submitted |
| `findReportingGaps()` | reporting-mcp | L | Diff computation |
| `getFieldValidationFailures()` | reporting-mcp | L | Field-level errors |
| `resubmitReport()` | reporting-mcp | M | Resubmit corrected report (gated) |

---

## Domain Acceptance Scenarios (Golden-Set Eval Harness)

| Scenario ID | Input | Expected Behavior |
|---|---|---|
| REG-EVAL-01 | Pre-deadline sweep | Identifies 3 missing reports |
| REG-EVAL-02 | All complete | "100% completeness — attestation ready" |
| REG-EVAL-03 | Systemic gap | Pattern: branch FX-BR-003 feed delay |
| REG-EVAL-04 | Resubmission approved | Calls resubmitReport |
| REG-EVAL-05 | Resubmission denied | Gap documented for manual handling |
| REG-EVAL-06 | Validation failures | Lists field-level errors with fix suggestions |

---

## Agentic Design Patterns

| Pattern | Application |
|---|---|
| 5 — Tool Use | MCP calls to reporting service |
| 12 — Human-in-the-Loop | Resubmission gate |
| 17 — Evaluation | Completeness metric |
| 19 — Prioritization | Gap ranking by deadline proximity |

---

## Python Sidecar Dependency

None. All completeness checks are deterministic diffs.
