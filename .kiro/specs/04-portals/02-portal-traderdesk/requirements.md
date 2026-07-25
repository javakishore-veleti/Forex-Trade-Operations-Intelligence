# Requirements Document — TraderDesk Portal

> **Technology-agnostic spec.** References **Technology Roles** from
> `01-initial-setup/01-technology-stack`. Contains no product names or versions.

## Introduction

The **TraderDesk Portal** is the customer-facing trader interface for the
Forex Trade Operations Intelligence platform. It is realized as a standalone
`FRONTEND_FRAMEWORK` application under `Portals/TraderDesk/`. It gives traders
visibility into their own trade lifecycle status, risk explanations, position
summaries, and trading book views — all in real time, sourced from
`Middleware/` service APIs.

The portal is **read-heavy and query-focused**: traders do not initiate
state-changing actions through this portal (trade capture goes through
dedicated front-office systems; recovery and exception management go through
the Admin Portal). The TraderDesk surfaces the intelligence layer — helping
traders understand *why* their risk looks the way it does, *where* their
trades are in the lifecycle, and *what* their book-level exposure is.

All example identifiers use the synthetic `FX-` prefix. All counterparty,
book, and region names are fictional.

---

## Glossary

- **TraderDeskPortal**: This `FRONTEND_FRAMEWORK` application
  (`Portals/TraderDesk/`).
- **Trader**: The primary user — a customer-facing FX trader who needs
  visibility into their own trades and positions.
- **TradeStatusView**: The portal screen showing the lifecycle status and
  timeline for a specific trade.
- **RiskExplanationView**: The portal screen showing a human-readable
  explanation of a trade's risk result, including contributing factors and
  rule traces.
- **PositionSummary**: The portal screen showing the trader's aggregate
  notional and risk position across all active trades, grouped by currency
  pair and region.
- **TradingBookView**: The portal screen showing all trades in a specific
  `tradingBookId` with their current status and risk level.
- **SyntheticData**: All example trade IDs, counterparty names, and region
  names in documentation and test fixtures use only `FX-` prefixed identifiers
  and fictional names.

---

## Requirements

### Requirement 1: Trade Lifecycle Status View

**User Story:** As a Trader, I want to look up any of my trades by ID and see
its current lifecycle status and timeline, so that I know exactly where it is
in processing without calling the operations desk.

#### Acceptance Criteria

1. THE TraderDeskPortal SHALL provide a search interface accepting a `tradeId`
   and navigating to a `TradeStatusView` for that trade.
2. THE `TradeStatusView` SHALL display: `tradeId`, `currencyPair`, `notionalAmount`,
   `direction`, `tradeDate`, `valueDate`, `regionCode`, `tradingBookId`,
   current `TradeStatus`, and the ordered lifecycle event timeline.
3. THE `TradeStatusView` SHALL visually indicate which lifecycle stages are
   complete, which is current, and which are pending, using an accessible
   step-indicator component (WCAG 2.1 AA).
4. WHEN a trade is in `TRADE_FAILED` or has active sequence anomalies, THE
   `TradeStatusView` SHALL display a clearly labelled status indicator; it
   SHALL NOT expose internal error details or stack traces.
5. THE `TradeStatusView` SHALL be read-only; it SHALL NOT surface any
   state-changing actions.

---

### Requirement 2: Risk Explanation View

**User Story:** As a Trader, I want to understand why my trade's risk was
calculated the way it was, so that I can explain it to my clients and
verify that the rules were applied correctly.

#### Acceptance Criteria

1. THE TraderDeskPortal SHALL provide a `RiskExplanationView` accessible from
   the `TradeStatusView` when a `RiskResult` exists for the trade.
2. THE `RiskExplanationView` SHALL display: `riskAmount`, `riskCurrency`,
   `riskLevel`, `ruleVersion`, the ordered list of `contributingFactors`
   (factor name, contribution amount, currency), and the list of `rulesFired`
   (synthetic rule identifiers).
3. THE `RiskExplanationView` SHALL present `contributingFactors` as a
   human-readable breakdown (e.g. a bar chart or table) showing each factor's
   share of the total risk amount.
4. THE `RiskExplanationView` SHALL compare the current risk result with the
   prior risk result for the same trade (if available), highlighting which
   factors changed and by how much.
5. ALL rule identifiers displayed SHALL be synthetic (e.g. `FX-RULE-APAC-042`);
   no real rule version strings from any production system SHALL appear.
6. THE `RiskExplanationView` SHALL be read-only.

---

### Requirement 3: Position Summary

**User Story:** As a Trader, I want to see my aggregate position across all
active trades grouped by currency pair and region, so that I have a consolidated
view of my exposure at a glance.

#### Acceptance Criteria

1. THE TraderDeskPortal SHALL provide a `PositionSummary` view showing aggregate
   notional and aggregate risk amount grouped by `currencyPair` and by
   `regionCode`, sourced from the Risk Calculation service's aggregation API.
2. THE `PositionSummary` SHALL display, for each group: total notional amount
   with currency, total risk amount, `RiskLevel` for the group, and trade count.
3. THE `PositionSummary` SHALL display the timestamp of the most recent risk
   calculation included in the aggregate, so that the trader knows how fresh
   the data is.
4. THE `PositionSummary` SHALL refresh on a configurable polling interval
   (default 60 seconds); the interval SHALL be externalized as configuration.
5. THE `PositionSummary` SHALL be accessible at WCAG 2.1 AA level;
   `RiskLevel` indicators SHALL NOT rely on colour alone.

---

### Requirement 4: Trading Book View

**User Story:** As a Trader, I want to see all trades in a specific trading
book with their status and risk level, so that I can monitor book-level
health without filtering through all my trades.

#### Acceptance Criteria

1. THE TraderDeskPortal SHALL provide a `TradingBookView` that lists all trades
   belonging to a selected `tradingBookId`, with their `tradeId`,
   `currencyPair`, `notionalAmount`, `direction`, `tradeDate`, current
   `TradeStatus`, and `RiskLevel`.
2. THE `TradingBookView` SHALL support sorting by `tradeDate`, `notionalAmount`,
   and `RiskLevel`.
3. THE `TradingBookView` SHALL support filtering by `TradeStatus` and
   `RiskLevel`.
4. EACH row in the `TradingBookView` SHALL link to the `TradeStatusView` for
   that trade.
5. THE `TradingBookView` SHALL paginate results and display no more than a
   configurable page size (default 25 trades per page).

---

### Requirement 5: Portal Non-Functional Requirements

**User Story:** As a platform engineer, I want the TraderDesk Portal to meet
baseline non-functional standards for accessibility, security, and configuration.

#### Acceptance Criteria

1. THE TraderDeskPortal SHALL be built using the `FRONTEND_FRAMEWORK` standalone
   component model (no legacy module declarations) at the same pinned major
   version as the other portals.
2. THE TraderDeskPortal SHALL be accessible at WCAG 2.1 AA level across all views.
3. THE TraderDeskPortal SHALL externalize all backend API base URLs and polling
   intervals as environment-specific configuration (not hard-coded).
4. THE TraderDeskPortal SHALL include a security placeholder that marks where
   token-based authentication will be wired in a later phase; no credential
   values SHALL be committed to the repository.
5. THE TraderDeskPortal SHALL handle API error responses gracefully: display a
   user-friendly message and allow retry; never expose raw error internals in
   the UI.
6. ALL fixture data and test data used in the TraderDeskPortal SHALL use only
   `SyntheticData` (`FX-` prefixed IDs, fictional names).
