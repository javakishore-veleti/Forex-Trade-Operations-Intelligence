# Requirements Document — Cutoff & Calendar Enforcement Agent

> **Technology-agnostic spec.** References **Technology Roles** from
> `01-initial-setup/01-technology-stack`. Contains no product names or versions.
> Inherits `architecture-golden-path/01-service-nfrs` for cross-cutting NFRs.

## Introduction

This feature defines the **Cutoff & Calendar Enforcement Agent** — a specialized
agent that detects post-cutoff events and trades landing on incorrect business
days, then holds them for the next business day with human approval.

This agent is implemented as an `AGENT_PLATFORM` workflow export. It does NOT
compute calendar dates — it relies on the deterministic business-calendar
service for all date math and enforces cutoff policies via hold actions.

All identifiers in examples use the synthetic `FX-` prefix.

---

## Glossary

- **CutoffCalendarAgent**: The `AGENT_PLATFORM` workflow enforcing cutoff policies.
- **RegionalCutoff**: The time boundary after which trades book to the next business day.
- **PostCutoffEvent**: A trade or event received after the regional cutoff.
- **BookingDateClassification**: Deterministic assignment of a trade to a business date.
- **HoldForNextDay**: A gated action that defers a trade to the next business date.

---

## Requirements

### Requirement 1: Post-Cutoff Event Detection

**User Story:** As a settlements operator, I want trades arriving after cutoff
detected automatically, so that they don't incorrectly book to today.

#### Acceptance Criteria

1. THE agent SHALL call `getPostCutoffEvents()` to retrieve events received
   after the regional cutoff.
2. THE agent SHALL verify each event's booking date via
   `classifyBookingDate()`.
3. THE agent SHALL identify trades that would incorrectly settle on the
   wrong business day.
4. THE agent SHALL detect DST transitions and holiday edges that shift cutoffs.

---

### Requirement 2: Approaching-Cutoff Warnings

**User Story:** As a trader, I want warning when my trades are near cutoff,
so that I can expedite or accept next-day booking.

#### Acceptance Criteria

1. THE agent SHALL call `getTradesApproachingCutoff()` to identify trades
   within a configurable window of cutoff (default: 30 minutes).
2. THE agent SHALL classify approaching trades by: will-make-it vs at-risk.
3. THE agent SHALL report time remaining and processing stage.

---

### Requirement 3: Hold for Next Business Day

**User Story:** As an operations supervisor, I want post-cutoff trades held
for next-day booking with my approval, so that settlement dates are correct.

#### Acceptance Criteria

1. WHEN a post-cutoff trade is detected, THE agent SHALL propose hold via
   HITL gate.
2. THE hold proposal SHALL include: trade details, intended vs correct
   business date, impact on settlement.
3. WHEN approved, THE agent SHALL call `holdForNextDay()`.
4. THE agent SHALL NOT auto-hold without human approval.

---

## Risk Classification

- **Inherent risk:** M (holds affect trade booking date and settlement)
- **HITL requirement:** Required for hold actions

---

## Model Tiers

| Role | Tier | Usage |
|------|------|-------|
| Detection | Deterministic (calendar service) | DST/holiday cutoff calculation |
| Reasoning | Deep (Opus-class) | Impact analysis, edge-case classification |
| Policy | Deterministic | Cutoff enforcement rules |
| Memory | Episodic | Prior cutoff violations |

---

## MCP Tools Called

| Tool | Source Service | Risk | Purpose |
|------|---------------|------|---------|
| `getRegionalCutoff(region)` | calendar-mcp | L | Current cutoff time for region |
| `getTradesApproachingCutoff()` | calendar-mcp | L | Trades within cutoff window |
| `getPostCutoffEvents()` | calendar-mcp | L | Events after cutoff |
| `classifyBookingDate()` | calendar-mcp | L | Deterministic date assignment |
| `holdForNextDay()` | trade-lifecycle-mcp | M | Defer trade to next day (gated) |

---

## Domain Acceptance Scenarios (Golden-Set Eval Harness)

| Scenario ID | Input | Expected Behavior |
|---|---|---|
| CUT-EVAL-01 | Trade arrives 5min after APAC cutoff | Post-cutoff detected, hold proposed |
| CUT-EVAL-02 | Hold approved | Calls holdForNextDay() |
| CUT-EVAL-03 | Trade 10min before cutoff | Warning: at-risk of missing cutoff |
| CUT-EVAL-04 | DST transition day | Correct cutoff applied (shifted) |
| CUT-EVAL-05 | Holiday edge (Friday → Monday) | Correct next-business-day identified |
| CUT-EVAL-06 | All trades before cutoff | "No post-cutoff events detected" |

---

## Agentic Design Patterns

| Pattern | Application |
|---|---|
| 5 — Tool Use | MCP calls to calendar and lifecycle services |
| 10 — Goal Setting | Correct booking-date assignment |
| 12 — Human-in-the-Loop | Hold approval gate |

---

## Python Sidecar Dependency

- None — all date/time logic is in the deterministic business-calendar
  service. No ML or statistical analysis required.
