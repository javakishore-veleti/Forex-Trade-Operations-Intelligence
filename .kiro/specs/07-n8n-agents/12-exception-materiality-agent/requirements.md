# Requirements Document — Exception Materiality Agent

> **Technology-agnostic spec.** References **Technology Roles** from
> `01-initial-setup/01-technology-stack`. Contains no product names or versions.
> Inherits `architecture-golden-path/01-service-nfrs` for cross-cutting NFRs.

## Introduction

This feature defines the **Exception Materiality Agent** — a specialized agent
that classifies unresolved exceptions before global close into material
(blockers) vs non-material (tolerable) categories. It uses deterministic
materiality classification from the policy engine and produces an approval
package for exceptions that can proceed.

This agent is implemented as an `AGENT_PLATFORM` workflow export. Materiality
classification is deterministic (policy/rules engine) — the agent explains
classifications and prioritizes for human review.

All identifiers in examples use the synthetic `FX-` prefix.

---

## Glossary

- **ExceptionMaterialityAgent**: The `AGENT_PLATFORM` workflow classifying exception materiality.
- **MaterialException**: An unresolved exception that blocks global close (high exposure, regulatory impact).
- **NonMaterialException**: An exception that can be approved for pass-through without material risk.
- **ApprovalPackage**: The consolidated report of non-material exceptions presented for bulk approval.
- **MaterialityPolicy**: The deterministic classification rules from the `RULES_ENGINE`.

---

## Requirements

### Requirement 1: Exception Retrieval and Classification

**User Story:** As an EOD supervisor, I want all unresolved exceptions
classified by materiality before global close, so that I can focus on
blockers and approve non-material items.

#### Acceptance Criteria

1. THE agent SHALL retrieve all unresolved exceptions via `getUnresolvedExceptions()`.
2. FOR EACH exception, THE agent SHALL retrieve exposure data via `getExposure(tradeId)`.
3. THE agent SHALL call `classifyMateriality()` (deterministic policy engine) for each exception.
4. THE agent SHALL categorize exceptions as: MATERIAL_BLOCKER, NON_MATERIAL, or REQUIRES_REVIEW.
5. THE agent SHALL rank material exceptions by exposure and regulatory impact.

---

### Requirement 2: Materiality Explanation

**User Story:** As a risk officer, I want to understand why each exception is
classified as material or non-material, so that I can validate the classification.

#### Acceptance Criteria

1. THE agent SHALL use the `ReasoningModel` to explain each classification in
   business terms.
2. THE explanation SHALL reference: exposure amount, regulatory implications,
   affected books/regions, and the policy rule that determined classification.
3. WHEN classification is REQUIRES_REVIEW, THE agent SHALL highlight what makes
   it ambiguous.

---

### Requirement 3: Approval Package with HITL Gate

**User Story:** As an EOD supervisor, I want a consolidated approval package
for non-material exceptions, so that I can approve them in bulk.

#### Acceptance Criteria

1. THE agent SHALL group non-material exceptions into an `ApprovalPackage`.
2. THE approval package SHALL include: exception count, total exposure,
   affected regions, and individual exception summaries.
3. THE package SHALL be presented at a HITL gate for bulk approval.
4. WHEN approved, THE exceptions SHALL be marked as "approved for pass-through".
5. WHEN denied, THE exceptions remain blocking.

---

## Risk Classification

- **Inherent risk:** M (exception approval changes close-process flow)
- **HITL requirement:** Mandatory for exception approval

---

## Model Tiers

| Role | Tier | Usage |
|------|------|-------|
| Policy | Deterministic (RULES_ENGINE) | Materiality classification |
| Reasoning | Deep (Opus-class) | Classification explanation |
| Prioritization | Deterministic + LLM | Ranking by exposure/impact |

---

## MCP Tools Called

| Tool | Source Service | Risk | Purpose |
|------|---------------|------|---------|
| `getUnresolvedExceptions()` | exception-mcp | L | All open exceptions |
| `getExposure(tradeId)` | risk-calculation-mcp | L | Exception exposure |
| `classifyMateriality()` | rules-engine-mcp | L | Deterministic classification |
| `approveExceptionBatch(ids)` | exception-mcp | M | Mark exceptions approved (gated) |

---

## Domain Acceptance Scenarios (Golden-Set Eval Harness)

| Scenario ID | Input | Expected Behavior |
|---|---|---|
| MAT-EVAL-01 | 5 unresolved exceptions | Classifies: 2 material, 3 non-material |
| MAT-EVAL-02 | All exceptions non-material | Presents bulk approval package |
| MAT-EVAL-03 | One material exception ($50M exposure) | Identifies as blocker, explains why |
| MAT-EVAL-04 | Approval granted | Marks non-material as approved |
| MAT-EVAL-05 | Approval denied | Exceptions remain blocking |
| MAT-EVAL-06 | No unresolved exceptions | "No exceptions pending" |

---

## Agentic Design Patterns

| Pattern | Application |
|---|---|
| 5 — Tool Use | MCP calls to exception and risk services |
| 12 — Human-in-the-Loop | Bulk exception approval gate |
| 19 — Prioritization | Ranking exceptions by materiality |

---

## Python Sidecar Dependency

None. Materiality classification is deterministic (policy engine).
