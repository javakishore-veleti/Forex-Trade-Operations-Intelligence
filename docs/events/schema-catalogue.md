# Event Schema Catalogue

> **Single source of truth** for all domain events on the FXOps event stream.
> All identifiers use synthetic `FX-` prefixed values. All service names are fictional.

---

## Event Envelope (Common Metadata)

Every event carries an `EventEnvelope` with these fields:

| Field | Type | Description |
|-------|------|-------------|
| `eventId` | UUID (string) | Unique identifier for this event instance; used as idempotency key |
| `eventType` | string (TradeEventType enum) | The specific event type |
| `schemaVersion` | integer | Schema version registered in the Schema Registry |
| `correlationId` | UUID (string) | Propagated from the upstream request/event for distributed tracing |
| `tradeId` | string (FX-prefixed) | The trade this event belongs to |
| `sourceService` | string | The spring.application.name of the producing service |
| `occurredAt` | ISO-8601 instant | Business time when the state change occurred |
| `publishedAt` | ISO-8601 instant | Broker write time |

---

## Trade Lifecycle Events

**Topic:** `fxops.trade.events`
**Schema Subject:** `fxops.trade.events-value`
**Partition Key:** `tradeId`

### TRADE_CAPTURED

| Field | Type | Description |
|-------|------|-------------|
| `tradeId` | string | Synthetic trade identifier (FX-prefixed) |
| `currencyPair` | object | `{baseCurrency, quoteCurrency, pairCode}` — the traded currency pair |
| `notionalAmount` | decimal | The notional value of the trade |
| `notionalCurrency` | string | ISO-4217 currency code for the notional |
| `direction` | string | BUY or SELL |
| `tradeDate` | ISO-8601 date | Date the trade was executed |
| `valueDate` | ISO-8601 date | Settlement date |
| `counterpartyId` | string | Synthetic counterparty identifier |
| `tradingBookId` | string | Synthetic trading book identifier |
| `regionCode` | string | Region where the trade was captured |

### TRADE_VALIDATED

| Field | Type | Description |
|-------|------|-------------|
| `tradeId` | string | Synthetic trade identifier |
| `validatedAt` | ISO-8601 instant | When validation completed |

### TRADE_ENRICHED

| Field | Type | Description |
|-------|------|-------------|
| `tradeId` | string | Synthetic trade identifier |
| `enrichedAt` | ISO-8601 instant | When enrichment completed |
| `marketDataSnapshotId` | string | Reference to the market data snapshot used |

### TRADE_RISK_CALCULATED

| Field | Type | Description |
|-------|------|-------------|
| `tradeId` | string | Synthetic trade identifier |
| `calculationId` | string | Unique ID of the risk calculation |
| `riskAmount` | decimal | Calculated risk value |
| `riskCurrency` | string | ISO-4217 currency of the risk amount |
| `riskLevel` | string | LOW, MEDIUM, HIGH, or CRITICAL |
| `ruleVersion` | string | Version of the risk rule set used |

### TRADE_BOOKED

| Field | Type | Description |
|-------|------|-------------|
| `tradeId` | string | Synthetic trade identifier |
| `bookedAt` | ISO-8601 instant | When booking completed |
| `bookingDate` | ISO-8601 date | Business date of booking |
| `regionCode` | string | Region where trade was booked |

### TRADE_ALLOCATED

| Field | Type | Description |
|-------|------|-------------|
| `tradeId` | string | Synthetic trade identifier |
| `allocatedAt` | ISO-8601 instant | When allocation completed |

### TRADE_CONFIRMED

| Field | Type | Description |
|-------|------|-------------|
| `tradeId` | string | Synthetic trade identifier |
| `confirmedAt` | ISO-8601 instant | When counterparty confirmation received |
| `counterpartyId` | string | Synthetic counterparty who confirmed |

### TRADE_SETTLED

| Field | Type | Description |
|-------|------|-------------|
| `tradeId` | string | Synthetic trade identifier |
| `settledAt` | ISO-8601 instant | When settlement completed |
| `settlementDate` | ISO-8601 date | Business date of settlement |
| `nostroAccount` | string | Synthetic nostro account used for settlement |

### TRADE_FAILED

| Field | Type | Description |
|-------|------|-------------|
| `tradeId` | string | Synthetic trade identifier |
| `failedAt` | ISO-8601 instant | When failure occurred |
| `failureReason` | string | Human-readable failure description |
| `failedStage` | string | The lifecycle stage where failure occurred |

---

## Trade Amendment & Cancellation Events

**Topic:** `fxops.trade.events`
**Schema Subject:** `fxops.trade.events-value`
**Partition Key:** `tradeId`

### TRADE_AMENDED

| Field | Type | Description |
|-------|------|-------------|
| `tradeId` | string | Synthetic trade identifier |
| `amendedAt` | ISO-8601 instant | When the amendment was applied |
| `amendedBy` | string | Synthetic service name that performed the amendment |
| `amendedFields` | array of AmendedField | List of changed fields |
| `amendmentReason` | string | Free-text reason for the amendment |

**AmendedField:**

| Field | Type | Description |
|-------|------|-------------|
| `fieldName` | string | Name of the field that changed |
| `previousValue` | string | Value before amendment |
| `newValue` | string | Value after amendment |

### TRADE_CANCELLED

| Field | Type | Description |
|-------|------|-------------|
| `tradeId` | string | Synthetic trade identifier |
| `cancelledAt` | ISO-8601 instant | When cancellation was applied |
| `cancelledBy` | string | Synthetic service name that cancelled the trade |
| `cancellationReason` | string | Reason for cancellation |

---

## Risk Events

### RISK_CALCULATION_REQUESTED

**Topic:** `fxops.risk.requests`
**Schema Subject:** `fxops.risk.requests-value`
**Partition Key:** `tradeId`

| Field | Type | Description |
|-------|------|-------------|
| `tradeId` | string | Synthetic trade identifier |
| `calculationRequestId` | UUID (string) | Unique request ID for correlation |
| `currencyPair` | object | `{baseCurrency, quoteCurrency, pairCode}` |
| `notionalAmount` | decimal | Trade notional value |
| `notionalCurrency` | string | ISO-4217 currency code |
| `regionCode` | string | Region of the trade |
| `tradingBookId` | string | Synthetic book ID |
| `marketDataSnapshotId` | string | Market data snapshot reference |
| `requestedAt` | ISO-8601 instant | When the request was made |

### RISK_CALCULATION_COMPLETED

**Topic:** `fxops.risk.results`
**Schema Subject:** `fxops.risk.results-value`
**Partition Key:** `tradeId`

| Field | Type | Description |
|-------|------|-------------|
| `tradeId` | string | Synthetic trade identifier |
| `calculationId` | string | Unique calculation result ID |
| `calculationRequestId` | UUID (string) | Matching request ID for correlation |
| `riskAmount` | decimal | Total calculated risk amount |
| `riskCurrency` | string | ISO-4217 currency of the risk amount |
| `riskLevel` | string | LOW, MEDIUM, HIGH, or CRITICAL |
| `contributingFactors` | array | `[{factorName, contributionAmount, currency}]` — factors summing to riskAmount |
| `ruleVersion` | string | Version of the risk rule set |
| `rulesFired` | array of string | Synthetic rule identifiers that contributed |
| `calculatedAt` | ISO-8601 instant | When calculation completed |

### RISK_CALCULATION_FAILED

**Topic:** `fxops.risk.results`
**Schema Subject:** `fxops.risk.results-value`
**Partition Key:** `tradeId`

| Field | Type | Description |
|-------|------|-------------|
| `tradeId` | string | Synthetic trade identifier |
| `calculationRequestId` | UUID (string) | Matching request ID |
| `failureReason` | string | Reason the calculation could not complete |
| `failedAt` | ISO-8601 instant | When the failure occurred |

---

## EOD Status Events

**Topic:** `fxops.eod.status`
**Schema Subject:** `fxops.eod.status-value`
**Partition Key:** `regionCode`

### REGIONAL_CLOSE_STARTED

| Field | Type | Description |
|-------|------|-------------|
| `regionCode` | string | Region beginning its close process |
| `globalBusinessDate` | ISO-8601 date | Business date being closed |
| `startedAt` | ISO-8601 instant | When the close process started |

### REGIONAL_CLOSE_READY

| Field | Type | Description |
|-------|------|-------------|
| `regionCode` | string | Region that is ready to close |
| `globalBusinessDate` | ISO-8601 date | Business date being closed |
| `readyAt` | ISO-8601 instant | When readiness was achieved |
| `branchCount` | integer | Total branches in the region |
| `completedBranchCount` | integer | Branches that have completed |

### REGIONAL_CLOSE_BLOCKED

| Field | Type | Description |
|-------|------|-------------|
| `regionCode` | string | Region that is blocked |
| `globalBusinessDate` | ISO-8601 date | Business date being closed |
| `blockedAt` | ISO-8601 instant | When the blocker was detected |
| `blockerCode` | string | Machine-readable blocker identifier |
| `blockerDescription` | string | Human-readable blocker description |

### REGIONAL_CLOSE_CLOSED

| Field | Type | Description |
|-------|------|-------------|
| `regionCode` | string | Region that has closed |
| `globalBusinessDate` | ISO-8601 date | Business date closed |
| `closedAt` | ISO-8601 instant | When the region was officially closed |

### GLOBAL_CONSOLIDATION_COMPLETED

| Field | Type | Description |
|-------|------|-------------|
| `globalBusinessDate` | ISO-8601 date | Business date that was consolidated |
| `consolidatedAt` | ISO-8601 instant | When consolidation completed |
| `regionSummary` | array | `[{regionCode, status}]` — summary per region |

**RegionSummaryEntry:**

| Field | Type | Description |
|-------|------|-------------|
| `regionCode` | string | Region identifier |
| `status` | string | Final status of the region for this business date |

---

## Replay & Reprocessing Events

**Topic:** `fxops.trade.events`
**Schema Subject:** `fxops.trade.events-value`
**Partition Key:** `tradeId`

### REPLAY_REQUESTED

| Field | Type | Description |
|-------|------|-------------|
| `tradeId` | string | Synthetic trade identifier to replay |
| `replayFromEventType` | string (TradeEventType) | Event type from which to restart replay |
| `requestedBy` | string | Synthetic service/agent name requesting replay |
| `requestedAt` | ISO-8601 instant | When the replay was requested |
| `approvalReference` | string | Human-approval reference from HITL gate (required, non-null) |

---

## Sequence Anomaly Events

**Topic:** `fxops.sequence.anomalies`
**Schema Subject:** `fxops.sequence.anomalies-value`
**Partition Key:** `tradeId`

### AnomalyEnvelope

| Field | Type | Description |
|-------|------|-------------|
| `tradeId` | string | Trade where anomaly was detected |
| `violationType` | string | MISSING_EVENT, MISSING_EVENT_RESOLVED, DUPLICATE_EVENT, CONFLICTING_REPLAY, OUT_OF_ORDER_EVENT |
| `details` | object | Violation-specific context fields |
| `detectedAt` | ISO-8601 instant | When the anomaly was detected |
| `correlationId` | UUID (string) | Correlation ID from the triggering event |
| `sequenceFactSnapshot` | object | Current SequenceFact at time of detection |
