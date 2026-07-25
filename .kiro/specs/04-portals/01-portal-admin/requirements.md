# Requirements Document — Admin Portal

> **Technology-agnostic spec.** References **Technology Roles** from
> `01-initial-setup/01-technology-stack`. Contains no product names or versions.

## Introduction

The **Admin Portal** is the operations and risk administration interface for the
Forex Trade Operations Intelligence platform. It is realized as a standalone
`FRONTEND_FRAMEWORK` application under `Portals/Admin/`. It gives operations
staff, risk managers, and platform administrators a unified web UI to investigate
trades, monitor EOD status, inspect risk aggregations, manage exceptions, and
interact with human-in-the-loop approval workflows triggered by `AGENT_PLATFORM`
agents.

The portal consumes data exclusively through the REST APIs exposed by
`Middleware/` services and through `AGENT_PLATFORM` webhook callbacks for
approval flows. It does **not** connect directly to any data store, event
stream, or agent runtime. All displayed data is read from microservice APIs;
all state-changing actions go through those same APIs (which enforce the
action-gate pattern).

All example identifiers use the synthetic `FX-` prefix. All organization,
counterparty, and region names are fictional.

---

## Glossary

- **AdminPortal**: This `FRONTEND_FRAMEWORK` application (`Portals/Admin/`).
- **OperationsUser**: A platform operations staff member who uses the portal
  to investigate trades and manage exceptions.
- **RiskManager**: A risk management staff member who uses the portal to
  monitor risk aggregations and approve limit-breach exceptions.
- **PlatformAdmin**: A platform administrator who manages portal configuration
  and user access (future phase — placeholder only at this stage).
- **TradeInvestigationView**: The portal screen showing a trade's lifecycle
  timeline, current state, risk result, and sequence anomalies for a given
  `tradeId`.
- **EODDashboard**: The portal screen showing per-region and global EOD close
  status for the current `GlobalBusinessDate`.
- **RiskAggregationView**: The portal screen showing risk totals aggregated by
  region, trading book, and globally.
- **ExceptionQueue**: The portal screen listing unresolved exceptions requiring
  triage or approval.
- **ApprovalWidget**: A portal UI component that displays a pending
  `AGENT_PLATFORM` approval request and provides approve/reject controls.
- **SyntheticData**: All example trade IDs, counterparty names, and region
  names in documentation and test fixtures use only `FX-` prefixed identifiers
  and fictional names.

---

## Requirements

### Requirement 1: Trade Investigation View

**User Story:** As an OperationsUser, I want to search for a trade by ID and
see its complete lifecycle timeline, current state, and any anomalies, so that
I can investigate issues without switching between tools.

#### Acceptance Criteria

1. THE AdminPortal SHALL provide a search interface accepting a `tradeId`
   (e.g. `FX-000001`) and navigating to a `TradeInvestigationView` for that
   trade.
2. THE `TradeInvestigationView` SHALL display: current `TradeStatus`, the
   ordered lifecycle event timeline (from the Trade Lifecycle service), the
   `RiskResult` if calculated (from the Risk Calculation service), and any
   active `SequenceViolation` anomalies (from the sequence anomaly topic or
   service).
3. THE `TradeInvestigationView` SHALL display whether any of the trade's
   events are in a `DLQTopic`, with the `dlq.failure.reason` visible.
4. THE `TradeInvestigationView` SHALL show the reconciliation status if a
   reconciliation result is available from the State Reconciliation service,
   including `violatedInvariants` and `permittedActions`.
5. ALL state-changing actions surfaced in the `TradeInvestigationView` (e.g.
   replay, reconcile) SHALL be displayed only when the corresponding
   `permittedAction` is present in the reconciliation result; the portal SHALL
   NOT surface actions not authorized by the deterministic service.
6. THE portal SHALL display only `SyntheticData` in all demonstration fixtures
   and documented screenshots.

---

### Requirement 2: EOD Status Dashboard

**User Story:** As an OperationsUser or RiskManager, I want a real-time EOD
status dashboard showing each region's close state and the global consolidation
status, so that I can identify which regions are blocking the global close
without polling multiple services.

#### Acceptance Criteria

1. THE AdminPortal SHALL provide an `EODDashboard` showing the current
   `RegionalCloseStatus` for each `RegionCode` (`APAC`, `EMEA`, `AMERICAS`)
   and the `GlobalConsolidationStatus`, sourced from the EOD Processing service.
2. THE `EODDashboard` SHALL refresh its data on a configurable polling interval
   (default 30 seconds) or on a server-sent event / websocket push if the
   backend supports it; the interval SHALL be externalized as configuration.
3. THE `EODDashboard` SHALL visually distinguish between `IN_PROGRESS`,
   `READY`, `BLOCKED`, and `CLOSED` regional states using distinct, accessible
   colour coding (WCAG 2.1 AA contrast minimum).
4. WHEN a region is in `BLOCKED` state, THE `EODDashboard` SHALL display the
   `blockerCode` and `blockerDescription` from the `REGIONAL_CLOSE_BLOCKED`
   event, with a link to the `ExceptionQueue` filtered to that region.
5. THE `EODDashboard` SHALL show the `GlobalBusinessDate` being processed and
   the time elapsed since each region started its close.

---

### Requirement 3: Risk Aggregation View

**User Story:** As a RiskManager, I want to see risk totals aggregated by
region and trading book so that I can monitor limit utilization and identify
high-risk concentrations.

#### Acceptance Criteria

1. THE AdminPortal SHALL provide a `RiskAggregationView` displaying risk totals
   grouped by `regionCode` and by `tradingBookId`, sourced from the Risk
   Calculation service.
2. THE `RiskAggregationView` SHALL show, for each group: total `riskAmount`,
   configured `Limit`, utilization percentage, and `RiskLevel` classification.
3. WHEN a group's risk total exceeds its `Limit`, THE `RiskAggregationView`
   SHALL highlight the breach visually and link to the affected trades.
4. THE `RiskAggregationView` SHALL display the timestamp of the most recent
   risk calculation included in each aggregate, so that staleness is visible.
5. THE `RiskAggregationView` SHALL be accessible at WCAG 2.1 AA level;
   breach indicators SHALL NOT rely on colour alone (must also use an icon
   or text label).

---

### Requirement 4: Exception Management Queue

**User Story:** As an OperationsUser, I want a unified exception queue listing
all unresolved platform exceptions so that I can prioritize triage and take
action without checking each service separately.

#### Acceptance Criteria

1. THE AdminPortal SHALL provide an `ExceptionQueue` listing unresolved
   exceptions from the EOD Processing service (late trades, branch blockers)
   and from the DLQ management layer (unresolved dead-lettered messages and
   poison messages).
2. THE `ExceptionQueue` SHALL support filtering by: exception type, `regionCode`,
   `tradeId`, and date range.
3. EACH exception entry SHALL display: exception type, affected `tradeId` (if
   applicable), `regionCode`, created-at timestamp, and current status.
4. WHEN an exception has a permitted resolution action (per the State
   Reconciliation or EOD Processing service), THE `ExceptionQueue` SHALL
   surface an action button; the action SHALL route through the corresponding
   service API and SHALL require confirmation before submission.
5. THE `ExceptionQueue` SHALL indicate which exceptions are `PoisonMessage`s
   (from DLQ management) and SHALL NOT surface an automatic replay button for
   them; instead it SHALL display a "requires manual review" label.

---

### Requirement 5: Agent Approval Widget (HITL Integration)

**User Story:** As an OperationsUser or RiskManager, I want to see pending
agent approval requests in the portal and approve or reject them without
switching to a separate chat tool, so that the HITL gate is part of my normal
operational workflow.

#### Acceptance Criteria

1. THE AdminPortal SHALL display an `ApprovalWidget` panel listing all pending
   `AGENT_PLATFORM` approval requests routed to the current user's role.
2. EACH approval request in the `ApprovalWidget` SHALL show: the requesting
   agent name (e.g. `eod-readiness-agent`), the proposed action description,
   the deterministic impact report generated by the service, the risk
   classification (M or H), and the `approvalReference` token.
3. THE `ApprovalWidget` SHALL provide **Approve** and **Reject** controls;
   both SHALL require a confirmation step before submission.
4. WHEN approved, THE portal SHALL POST the approval decision (including the
   `approvalReference`) to the configured `AGENT_PLATFORM` webhook endpoint;
   WHEN rejected, it SHALL POST the rejection with an optional operator note.
5. THE portal SHALL NOT allow an approval to be submitted without the
   `approvalReference` being present and non-empty.
6. THE `ApprovalWidget` SHALL refresh pending requests on a configurable
   polling interval (default 15 seconds).

---

### Requirement 6: Portal Non-Functional Requirements

**User Story:** As a platform engineer, I want the Admin Portal to meet
baseline non-functional standards for accessibility, security, and
configuration so that it is deployable and auditable.

#### Acceptance Criteria

1. THE AdminPortal SHALL be built using the `FRONTEND_FRAMEWORK` standalone
   component model (no legacy module declarations).
2. THE AdminPortal SHALL be accessible at WCAG 2.1 AA level across all views.
3. THE AdminPortal SHALL externalize all backend API base URLs and polling
   intervals as environment-specific configuration (not hard-coded).
4. THE AdminPortal SHALL include a security placeholder that marks where
   token-based authentication will be wired in a later phase; no credential
   values SHALL be committed to the repository.
5. THE AdminPortal SHALL handle API error responses gracefully: display a
   user-friendly error message and allow the user to retry; never expose raw
   stack traces or internal service error details in the UI.
6. ALL fixture data, documentation screenshots, and test data used in the
   AdminPortal SHALL use only `SyntheticData` (`FX-` prefixed IDs, fictional
   names).
