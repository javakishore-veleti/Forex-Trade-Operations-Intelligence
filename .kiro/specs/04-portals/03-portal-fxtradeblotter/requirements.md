# Requirements Document — FX Trade Blotter Portal

> **Technology-agnostic spec.** References **Technology Roles** from
> `01-initial-setup/01-technology-stack`. Contains no product names or versions.

## Introduction

The **FX Trade Blotter Portal** is the broker-facing real-time blotter for the
Forex Trade Operations Intelligence platform. It is realized as a standalone
`FRONTEND_FRAMEWORK` application under `Portals/FXTradeBlotter/`. It gives
brokers a live view of positions, currency-pair exposure, settlement status,
and counterparty exposure across the trades they are responsible for.

Unlike the TraderDesk (which is trade-by-trade, trader-focused), the Blotter
is **position-centric and counterparty-centric**: brokers care about aggregate
exposure across many trades, settlement risk, and counterparty concentration.
The portal is read-only; all data is sourced from `Middleware/` service APIs.

All example identifiers use the synthetic `FX-` prefix. All counterparty,
region, and book names are fictional.

---

## Glossary

- **FXTradeBlotterPortal**: This `FRONTEND_FRAMEWORK` application
  (`Portals/FXTradeBlotter/`).
- **Broker**: The primary user — a broker who monitors live FX positions,
  exposure, and settlement status.
- **LivePositionView**: The portal screen showing real-time aggregate positions
  by currency pair for the broker's portfolio.
- **ExposureView**: The portal screen showing net exposure by currency pair and
  region, derived from risk aggregation data.
- **SettlementStatusView**: The portal screen showing pending and at-risk
  settlements for the current and next business date.
- **CounterpartyExposureView**: The portal screen showing aggregate exposure
  and limit utilization per counterparty.
- **SyntheticData**: All example trade IDs, counterparty names, and region
  names in documentation and test fixtures use only `FX-` prefixed identifiers
  and fictional names.

---

## Requirements

### Requirement 1: Live Position View

**User Story:** As a Broker, I want to see my real-time aggregate positions
by currency pair, so that I can monitor my book without waiting for end-of-day
reports.

#### Acceptance Criteria

1. THE FXTradeBlotterPortal SHALL provide a `LivePositionView` showing
   aggregate net notional by `currencyPair` across all active trades (status
   not `SETTLED`, `CANCELLED`, or `FAILED`), sourced from the Trade Lifecycle
   and Risk Calculation services.
2. THE `LivePositionView` SHALL display, for each `currencyPair`: net long
   notional, net short notional, net position (long minus short), and total
   trade count.
3. THE `LivePositionView` SHALL refresh on a configurable polling interval
   (default 30 seconds); the interval SHALL be externalized as configuration.
4. THE `LivePositionView` SHALL highlight currency pairs where the net position
   exceeds a configurable display threshold, using an accessible visual
   indicator (WCAG 2.1 AA — not colour alone).
5. EACH currency pair row SHALL link to a filtered `SettlementStatusView`
   showing only trades for that pair.

---

### Requirement 2: Exposure View

**User Story:** As a Broker, I want to see my net FX exposure grouped by
currency pair and region, so that I can identify concentration risk before
the end of day.

#### Acceptance Criteria

1. THE FXTradeBlotterPortal SHALL provide an `ExposureView` showing total risk
   amount and `RiskLevel` grouped by `currencyPair` and by `regionCode`,
   sourced from the Risk Calculation service's aggregation API.
2. THE `ExposureView` SHALL display, for each group: total `riskAmount`,
   `riskCurrency`, `RiskLevel`, configured `Limit` (if available), and
   utilization percentage.
3. WHEN a group's exposure exceeds its `Limit`, THE `ExposureView` SHALL
   display a breach indicator accessible at WCAG 2.1 AA level (not colour alone).
4. THE `ExposureView` SHALL display the data freshness timestamp (time of last
   risk calculation included in the aggregate).
5. THE `ExposureView` SHALL be read-only.

---

### Requirement 3: Settlement Status View

**User Story:** As a Broker, I want to see the settlement status of all trades
due to settle today and tomorrow, so that I can proactively manage settlement
risk before cutoff.

#### Acceptance Criteria

1. THE FXTradeBlotterPortal SHALL provide a `SettlementStatusView` listing all
   trades with `valueDate` equal to the current `GlobalBusinessDate` or the
   next `GlobalBusinessDate`, grouped by `valueDate`.
2. THE `SettlementStatusView` SHALL display for each trade: `tradeId`,
   `currencyPair`, `notionalAmount`, `direction`, `counterpartyId`,
   `TradeStatus`, and any DLQ or anomaly flags.
3. THE `SettlementStatusView` SHALL visually flag trades at settlement risk —
   specifically trades in any status other than `CONFIRMED` or `SETTLED` that
   are due to settle today — using an accessible indicator.
4. THE `SettlementStatusView` SHALL support filtering by `valueDate`,
   `TradeStatus`, and `currencyPair`.
5. THE `SettlementStatusView` SHALL paginate results with a configurable page
   size (default 25 trades per page).
6. EACH trade row SHALL link to the `TradeStatusView` in the TraderDesk portal
   (deep-link by `tradeId`) if cross-portal navigation is configured.

---

### Requirement 4: Counterparty Exposure View

**User Story:** As a Broker, I want to see aggregate exposure and limit
utilization per counterparty, so that I can identify counterparties approaching
their credit limits before new trades are submitted.

#### Acceptance Criteria

1. THE FXTradeBlotterPortal SHALL provide a `CounterpartyExposureView` listing
   all counterparties with active trades, showing aggregate `riskAmount`,
   configured `Limit`, utilization percentage, and `RiskLevel`.
2. THE `CounterpartyExposureView` SHALL source data from the Risk Calculation
   and State Reconciliation services' counterparty-level aggregation APIs.
3. WHEN a counterparty's utilization exceeds 80% of their `Limit`, THE
   `CounterpartyExposureView` SHALL display a warning indicator (WCAG 2.1 AA).
4. WHEN a counterparty's utilization exceeds 100% of their `Limit`, THE
   `CounterpartyExposureView` SHALL display a breach indicator (WCAG 2.1 AA —
   not colour alone).
5. EACH counterparty row SHALL be expandable to show the individual trades
   contributing to that counterparty's exposure.
6. THE `CounterpartyExposureView` SHALL display the data freshness timestamp.

---

### Requirement 5: Portal Non-Functional Requirements

**User Story:** As a platform engineer, I want the FX Trade Blotter Portal to
meet baseline non-functional standards for accessibility, security, and
configuration.

#### Acceptance Criteria

1. THE FXTradeBlotterPortal SHALL be built using the `FRONTEND_FRAMEWORK`
   standalone component model (no legacy module declarations) at the same
   pinned major version as the other portals.
2. THE FXTradeBlotterPortal SHALL be accessible at WCAG 2.1 AA level across
   all views.
3. THE FXTradeBlotterPortal SHALL externalize all backend API base URLs and
   polling intervals as environment-specific configuration (not hard-coded).
4. THE FXTradeBlotterPortal SHALL include a security placeholder that marks
   where token-based authentication will be wired in a later phase; no
   credential values SHALL be committed to the repository.
5. THE FXTradeBlotterPortal SHALL handle API error responses gracefully:
   display a user-friendly message and allow retry; never expose raw error
   internals in the UI.
6. ALL fixture data and test data used in the FXTradeBlotterPortal SHALL use
   only `SyntheticData` (`FX-` prefixed IDs, fictional counterparty names).
