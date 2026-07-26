# Platform Use Cases

> 50 concrete scenarios showing how personas interact with the Forex Trade Operations Intelligence platform through portals, agents, and sidecars.

---

## Table of Contents

- [Personas](#personas)
- [Category A: Trade Lifecycle & Investigation](#category-a-trade-lifecycle--investigation)
- [Category B: Risk & Rules](#category-b-risk--rules)
- [Category C: End-of-Day Operations](#category-c-end-of-day-operations)
- [Category D: Event & Data Integrity](#category-d-event--data-integrity)
- [Category E: Observability & Correlation](#category-e-observability--correlation)
- [Category F: Capacity, Cost & Recovery](#category-f-capacity-cost--recovery)
- [Category G: Multi-Agent & Supervisor](#category-g-multi-agent--supervisor)

---

## Personas

| Persona | Portal / Tool | Role Description |
|---------|--------------|-----------------|
| **FX Trader** | TraderDesk | Customer-facing trader who needs visibility into trade lifecycle status, risk explanations, position summaries, and book-level views. Read-only consumer of intelligence. |
| **Broker / Sales Desk** | FX Blotter | Monitors live aggregate positions, currency-pair exposure, settlement status, and counterparty concentration. Position-centric and counterparty-centric view. |
| **Operations Staff (Trade Ops)** | Admin Portal | Investigates trade issues, manages exception queues, triages DLQ messages, monitors EOD progress, and executes approved recovery actions. |
| **Risk Manager** | Admin Portal | Monitors risk aggregations, reviews limit breaches, approves high-risk agent actions, and oversees counterparty exposure narratives. |
| **Platform Administrator** | Admin Portal | Manages portal configuration, user access, and agent deployment. Reviews system-wide health and scaling decisions. |
| **Rules Owner / Quantitative Analyst** | Admin Portal | Authors and deploys Drools business rules, reviews rule impact analyses, approves rule rollbacks, and runs shadow simulations. |
| **Compliance Officer** | Admin Portal | Reviews regulatory reporting completeness, exception materiality assessments, and audit trails for approved actions. |
| **Development / SRE Team** | Grafana / Kibana / Jaeger | Monitors infrastructure health, traces distributed requests, investigates performance anomalies, and reviews deployment correlations. |

---

## Category A: Trade Lifecycle & Investigation

### A1. Trade Stalled Investigation

- **Personas:** Operations Staff (Admin Portal), FX Trader (TraderDesk — observes status)
- **Trigger:** Trader notices trade FX-004217 stuck in `ENRICHMENT_PENDING` for 20 minutes; calls ops desk.
- **Agents invoked:**
  1. **Supervisor** — classifies intent as lifecycle investigation, routes to Lifecycle Reconstruction.
  2. **Lifecycle Reconstruction** — calls `getTrade()`, `getTradeEvents()`, `getExpectedLifecycle()`, `getBusinessCalendar()`; identifies enrichment event never emitted.
- **Portal interactions:** Ops searches FX-004217 in Trade Investigation View → sees timeline with gap after VALIDATED → agent explanation panel shows "enrichment-service did not emit EnrichedEvent; last health check: degraded."
- **HITL gate:** Agent proposes `replayTradeEvent(FX-004217, VALIDATED)` (Risk M) → Ops reviews impact report → clicks Approve.
- **Outcome:** Trade resumes processing; timeline updates in TraderDesk showing ENRICHED stage complete.

### A2. Missing Event Detection

- **Personas:** Operations Staff (Admin Portal)
- **Trigger:** Event Sequence Processor emits a `SEQUENCE_VIOLATION` envelope — trade FX-007831 missing `RISK_CALCULATED` between `ENRICHED` and `BOOKED`.
- **Agents invoked:**
  1. **Event Integrity** — calls `getSequenceFacts(FX-007831)`; confirms gap is real, not reordering.
  2. **Lifecycle Reconstruction** — calls `getServiceProcessingStatus()` for risk-calculation service; finds it processed the trade but Kafka producer failed.
- **Portal interactions:** Exception Queue shows new entry: "Missing RISK_CALCULATED for FX-007831." Ops clicks into Trade Investigation View → sees gap highlighted in red on timeline.
- **HITL gate:** Agent proposes `requestReplay(FX-007831, ENRICHED)` (Risk M) → Ops approves.
- **Outcome:** Risk-calculation service reprocesses; missing event appears; sequence violation auto-resolves.

### A3. Duplicate Event Handling

- **Personas:** Operations Staff (Admin Portal)
- **Trigger:** Duplicate Business-Effect Guard detects two `SETTLEMENT_INSTRUCTED` events for FX-001455 with same idempotency key but divergent amounts ($1.2M vs $1.5M).
- **Agents invoked:**
  1. **Duplicate Effect Guard** — calls `checkIdempotencyConsumed(key)`, `findDuplicateSettlementInstruction()`; confirms double-booking risk.
  2. **Lifecycle Reconstruction** — reconstructs timeline showing amendment arrived during retry window.
- **Portal interactions:** Exception Queue shows critical entry: "Duplicate settlement effect — FX-001455." Trade Investigation View shows both events side-by-side with diff highlighted.
- **HITL gate:** Agent proposes `reverseDuplicateEffect(FX-001455, instructionId=SI-9982)` (Risk H) → dry-run report shows reversal impact → Ops approves.
- **Outcome:** Duplicate instruction reversed; settlement proceeds with correct $1.5M amount.

### A4. Trade Amendment Downstream Tracking

- **Personas:** Operations Staff (Admin Portal), FX Trader (TraderDesk)
- **Trigger:** `TradeAmended` event for FX-002190 (notional changed from $5M to $3M).
- **Agents invoked:**
  1. **Amendment Ripple** — calls `findDownstreamEffects(FX-002190)` via Neo4j, `checkRiskRecalcTriggered()`, `checkSettlementWithdrawn()`, `checkReportAmended()`.
  2. Finds: risk recalc fired ✓, settlement withdrawn ✓, but regulatory report NOT amended.
- **Portal interactions:** Ops sees amendment ripple report in Trade Investigation View showing 3 downstream checks (2 green, 1 red). Links to the regulatory reporting gap.
- **HITL gate:** Agent proposes `requestMissingRecalc()` for reporting service (Risk M) → Ops approves.
- **Outcome:** Report resubmitted with amended notional. Trader sees updated risk in TraderDesk Risk Explanation View.

### A5. Trade Cancellation Ripple

- **Personas:** Operations Staff (Admin Portal), Broker (FX Blotter)
- **Trigger:** `TradeCancelled` event for FX-003001 (EUR/USD $10M spot).
- **Agents invoked:**
  1. **Amendment Ripple** — traces cancellation through Neo4j dependency graph: risk position, settlement queue, counterparty exposure, EOD aggregation.
  2. Finds: settlement instruction still pending at CLS, counterparty limit not released.
- **Portal interactions:** Ops views cancellation ripple in Admin Portal. Broker sees position update in Live Position View (EUR/USD net position decreases). Counterparty Exposure View shows stale limit utilization.
- **HITL gate:** Agent proposes `withdrawSettlement(FX-003001)` + `releaseCounterpartyReservation(FX-003001)` (Risk H) → Ops approves both.
- **Outcome:** Settlement withdrawn, counterparty limit freed. Blotter reflects updated position within next polling cycle.

### A6. Cross-System State Mismatch

- **Personas:** Operations Staff (Admin Portal)
- **Trigger:** Scheduled cross-system sweep by State Divergence agent finds trade FX-005500 is `BOOKED` in Postgres, `ENRICHED` in MongoDB, and `RISK_CALCULATED` in Redis cache.
- **Agents invoked:**
  1. **State Divergence** — calls `queryTradeState()`, `getTradeDocument()`, `getCachedTradeState()`, `getLatestDomainEvent()`, `evaluateCanonicalState()`.
  2. Canonical state (from event history): `BOOKED`. MongoDB and Redis are stale.
- **Portal interactions:** Trade Investigation View shows reconciliation panel: three stores listed with their states, canonical state highlighted. Violated invariants listed: "MongoDB projection lagging by 2 events."
- **HITL gate:** Agent proposes `startReconciliation(FX-005500)` (Risk M) → Ops reviews permitted actions → approves.
- **Outcome:** MongoDB projection replayed from event log; Redis cache invalidated and rebuilt. All stores converge to `BOOKED`.

### A7. Trade Replay After Failure

- **Personas:** Operations Staff (Admin Portal)
- **Trigger:** After A1-style investigation confirms trade FX-008120 is stuck due to transient infra failure (now resolved).
- **Agents invoked:**
  1. **Transaction Recovery Coordinator** — multi-phase: calls `verifyNoSettlement()`, `checkReplayKey()`, `invalidateCache()` (safety checks).
  2. Generates ordered recovery plan: invalidate cache → replay from VALIDATED → verify state progression → close case.
- **Portal interactions:** Ops sees recovery plan in Approval Widget with 4 steps listed. Each step shows dry-run result. Progress bar updates as steps execute.
- **HITL gate:** Full recovery plan requires single approval (Risk H) — but each step is verified before proceeding to next.
- **Outcome:** Trade FX-008120 progresses through remaining lifecycle stages. Audit trail records each recovery step with timestamps.

### A8. Lifecycle Timeline Audit

- **Personas:** Compliance Officer (Admin Portal), Operations Staff (Admin Portal)
- **Trigger:** Compliance requests full audit of trade FX-000042 processing for regulatory inquiry.
- **Agents invoked:**
  1. **Lifecycle Reconstruction** — calls `getTrade()`, `getTradeEvents()`, `getTradeAuditHistory()` with full history flag.
  2. Produces complete timeline including: all state transitions, processing timestamps, service versions, operator interventions, and any agent-proposed actions with approval records.
- **Portal interactions:** Trade Investigation View renders full timeline with expandable audit entries. Compliance downloads timeline as structured report.
- **HITL gate:** None (read-only, Risk L).
- **Outcome:** Complete auditable record of trade's journey through all 9 lifecycle stages with timestamps and responsible services.

### A9. Orphan Event Investigation

- **Personas:** Operations Staff (Admin Portal)
- **Trigger:** Event Sequence Processor detects `SETTLEMENT_CONFIRMED` event referencing trade FX-099001 which has no prior events in any topic.
- **Agents invoked:**
  1. **Event Integrity** — calls `getSequenceFacts(FX-099001)`; finds zero prior events.
  2. **Lifecycle Reconstruction** — calls `getTrade(FX-099001)` across all stores; trade does not exist in Postgres or MongoDB.
  3. Agent concludes: orphan event from external system or data corruption.
- **Portal interactions:** Exception Queue shows "Orphan event — no trade found for FX-099001." Agent explanation: "Event references non-existent trade. Likely mis-routed from external feed."
- **HITL gate:** Agent proposes `quarantineEvent(FX-099001, SETTLEMENT_CONFIRMED)` (Risk M) → Ops approves.
- **Outcome:** Event quarantined for manual review. No downstream processing triggered. Incident logged for feed investigation.

### A10. Batch Trade Status Check

- **Personas:** FX Trader (TraderDesk), Operations Staff (Admin Portal)
- **Trigger:** Trader asks supervisor agent: "What's the status of my APAC EUR/JPY trades from today?"
- **Agents invoked:**
  1. **Supervisor** — classifies as batch query, routes to Lifecycle Reconstruction with filter parameters.
  2. **Lifecycle Reconstruction** — calls `getTrade()` with filters: region=APAC, pair=EUR/JPY, tradeDate=today; returns 14 trades.
  3. Summarizes: 11 SETTLED, 2 RISK_CALCULATED (in progress), 1 FAILED.
- **Portal interactions:** Trader views Trading Book View filtered to EUR/JPY → sees 14 trades with status indicators. The 1 FAILED trade (FX-006700) shows red indicator; trader clicks through to Trade Status View.
- **HITL gate:** None (read-only, Risk L).
- **Outcome:** Trader has consolidated view; escalates FX-006700 to ops for investigation.

---

## Category B: Risk & Rules

### B1. Risk Spike Explanation

- **Personas:** FX Trader (TraderDesk), Risk Manager (Admin Portal)
- **Trigger:** Trader sees risk on FX-002480 jumped from $12K to $89K after re-calculation; asks "why did my risk increase?"
- **Agents invoked:**
  1. **Supervisor** — routes to Risk Explainability agent.
  2. **Risk Explainability** — calls `getRiskResult(FX-002480)` (returns current/previous results, contributing factors, rules fired), `getRuleTrace()`, `getMarketSnapshot()`.
  3. Produces multi-factor explanation: "EUR/GBP volatility spike (+340bps) triggered rule FX-RULE-VOL-017; notional×volatility contribution went from $8K to $74K."
- **Portal interactions:** Trader opens Risk Explanation View for FX-002480 → sees factor breakdown bar chart showing volatility as dominant contributor. Prior vs current comparison highlights the change.
- **HITL gate:** None (read-only, Risk L).
- **Outcome:** Trader understands the spike is market-driven, not a rule error. Can explain to client.

### B2. Rule Deployment Impact Analysis

- **Personas:** Rules Owner (Admin Portal), Risk Manager (Admin Portal)
- **Trigger:** Python sidecar detects firing-rate anomaly: rule package 7.14 deployed 2 hours ago; EUR/GBP rejection rate +28%.
- **Agents invoked:**
  1. **Rule Impact** — calls `getRuleFiringStats()`, `compareRuleBehavior(v7.13, v7.14)`, `findConflictingRules()`.
  2. Identifies: new rule FX-RULE-EUR-088 has overly broad condition matching all EUR crosses, not just EUR/GBP as intended.
- **Portal interactions:** Rules Owner sees alert in Exception Queue: "Rule v7.14 over-rejecting EUR crosses." Impact report shows 142 trades affected, $340M notional blocked.
- **HITL gate:** Agent proposes `requestRuleRollback(v7.14 → v7.13)` (Risk H) → Risk Manager reviews impact report → approves rollback.
- **Outcome:** Rule rolled back to v7.13. Affected trades reprocessed. Rules Owner fixes condition for next deployment.

### B3. Rule Rollback Approval

- **Personas:** Rules Owner (Admin Portal), Risk Manager (Admin Portal)
- **Trigger:** Rules Owner manually requests rollback of rule FX-RULE-APAC-042 after noticing incorrect threshold in production.
- **Agents invoked:**
  1. **Rule Impact** — calls `simulateRule()` with previous version; compares firing patterns.
  2. Produces impact report: "Rollback affects 23 trades currently held by rule. 19 would pass with prior threshold."
- **Portal interactions:** Approval Widget shows pending rollback request with simulation results. Risk Manager reviews: which trades release, what their risk levels are.
- **HITL gate:** `requestRuleRollback()` (Risk H) → Risk Manager approves with note: "Confirmed threshold was incorrect per change ticket FX-CHG-0091."
- **Outcome:** Rule reverted. 19 trades released for continued processing. 4 remain held (correctly) by other rules.

### B4. Currency Pair Coverage Gap

- **Personas:** Rules Owner (Admin Portal), Risk Manager (Admin Portal)
- **Trigger:** Python sidecar detects fallback-firing-rate for TRY/ZAR exceeds baseline by 5x — no specific rule exists for this exotic pair.
- **Agents invoked:**
  1. **Currency-Pair Coverage** — calls `getRuleCoverageMatrix()`, `getFallbackFiringRate(TRY/ZAR)`, `getUncoveredPairs()`, `simulateRuleGap()`.
  2. Identifies: TRY/ZAR trades hitting generic fallback rule; risk being underestimated by ~40% vs similar exotic pairs with dedicated rules.
- **Portal interactions:** Rules Owner sees coverage gap alert. Simulation shows: "12 TRY/ZAR trades in last week, all using fallback. Estimated mispricing: $180K aggregate."
- **HITL gate:** None for detection (Risk L). Follow-up rule authoring goes through B5 (Shadow Rule Simulator).
- **Outcome:** Rules Owner prioritizes TRY/ZAR rule authoring. Interim: Risk Manager adds manual watch on the pair.

### B5. Shadow Rule Simulation (NL to DRL)

- **Personas:** Rules Owner (Admin Portal), Risk Manager (Admin Portal)
- **Trigger:** Rules Owner submits natural-language rule: "For TRY/ZAR spot trades over $1M, apply 2.5x volatility multiplier."
- **Agents invoked:**
  1. **Shadow Rule Simulator** — converts NL to DRL via Opus; calls `loadShadowRule(drl)` on shadow Spring Boot pod.
  2. Replays 30 days of historical TRY/ZAR events: `replayHistoricalEvents(30d)`.
  3. Calls `diffAgainstProduction()` — shows 47 trades would have had higher risk; no limit breaches triggered.
- **Portal interactions:** Rules Owner sees: generated DRL code, replay results (47 trades affected, average risk increase +$4.2K), production diff chart.
- **HITL gate:** `deployRule(shadow→production)` (Risk H) → Risk Manager reviews diff → approves promotion.
- **Outcome:** New TRY/ZAR rule live in production. Coverage gap from B4 closed.

### B6. Counterparty Exposure Narrative

- **Personas:** Risk Manager (Admin Portal), Broker (FX Blotter)
- **Trigger:** Scheduled EOD counterparty review; Risk Manager requests narrative for counterparty FX-CP-NORDIC-7.
- **Agents invoked:**
  1. **Counterparty Exposure Narrative** — calls `getCounterpartyExposure(FX-CP-NORDIC-7)`, `getLimits()`, `getConcentration()`, `getCollateral()`, `getPriorDayExposure()`.
  2. Produces narrative: "Exposure up 22% day-over-day. Driven by 3 new EUR/SEK trades. Utilization now 87% of limit. Collateral covers 60%."
- **Portal interactions:** Risk Manager reads narrative in Admin Portal. Broker sees 87% utilization warning (amber) in Counterparty Exposure View.
- **HITL gate:** None (Risk L, read/explain only).
- **Outcome:** Risk Manager decides to hold new trades for FX-CP-NORDIC-7 pending collateral top-up. Communicates to sales desk.

### B7. Limit Breach Notification

- **Personas:** Risk Manager (Admin Portal), Broker (FX Blotter)
- **Trigger:** Risk Calculation service detects trading book FX-BOOK-EMEA-03 exceeds configured limit ($50M risk; limit $45M).
- **Agents invoked:**
  1. **Risk Explainability** — called to explain breach: which trades pushed over limit, contributing factors.
  2. Identifies: trade FX-009100 (GBP/CHF $20M) was the tipping trade; volatility factor is dominant.
- **Portal interactions:** Admin Portal Risk Aggregation View shows FX-BOOK-EMEA-03 in breach (red icon + "BREACH" label). Broker sees breach indicator in Exposure View. Risk Manager clicks into affected trades list.
- **HITL gate:** None for notification. If agent proposes `holdNewTrades(FX-BOOK-EMEA-03)` → Risk Manager approval required (Risk M).
- **Outcome:** Risk Manager aware of breach. Decides whether to hold new bookings or request limit increase from credit.

### B8. Risk Recalculation Request

- **Personas:** Operations Staff (Admin Portal), FX Trader (TraderDesk)
- **Trigger:** Trade FX-004500 market data was stale during original calculation (detected by Market Data Staleness agent). Now data is fresh.
- **Agents invoked:**
  1. **Risk Explainability** — confirms original calculation used stale EUR/USD rate (15 minutes old at time of calc).
  2. **Market Data Staleness** — confirms current feed is live and fresh.
- **Portal interactions:** Ops sees in Trade Investigation View: "Risk calculated with stale market data. Current data available." Action button: "Request Recalculation."
- **HITL gate:** `requestRiskRecalculation(FX-004500)` (Risk M) → Ops approves.
- **Outcome:** Risk service recalculates with fresh data. Trader sees updated (lower) risk in Risk Explanation View. Position Summary updates.

---

## Category C: End-of-Day Operations

### C1. Region Blocked — Identify and Clear Blocker

- **Personas:** Operations Staff (Admin Portal), Risk Manager (Admin Portal)
- **Trigger:** EOD Dashboard shows EMEA region status: `BLOCKED`. Blocker code: `UNPROCESSED_TRADES_EXCEED_THRESHOLD`.
- **Agents invoked:**
  1. **EOD Readiness** — calls `getRegionalCloseStatus(EMEA)`, `getUnprocessedTradeCount(EMEA)`, `getLateTradeMateriality(EMEA)`.
  2. Identifies: 7 unprocessed trades totaling $2.1M notional. 5 are immaterial ($50K total); 2 are material ($2.05M).
  3. **Exception Materiality** — classifies: 5 non-material (can be excepted), 2 material (must resolve).
- **Portal interactions:** EOD Dashboard shows EMEA blocked with link to Exception Queue. Ops sees 7 exceptions split into "immaterial" and "material" groups. Material trades link to Trade Investigation View.
- **HITL gate:** Agent proposes `approveException(EMEA, [FX-011001..FX-011005])` for immaterial trades (Risk M) → Ops approves. Material trades require individual investigation.
- **Outcome:** 5 immaterial trades excepted; EMEA unblocked pending 2 material trade resolutions.

### C2. Late Trade Materiality Assessment

- **Personas:** Operations Staff (Admin Portal), Compliance Officer (Admin Portal)
- **Trigger:** Trade FX-012300 ($800K EUR/JPY) arrives 4 minutes before APAC cutoff; processing won't complete in time.
- **Agents invoked:**
  1. **EOD Readiness** — calls `getLateTradeMateriality(APAC)`.
  2. **Exception Materiality** — calls `getExposure(FX-012300)`, `classifyMateriality()` via Drools; returns: MATERIAL (above $500K threshold for APAC).
- **Portal interactions:** EOD Dashboard shows APAC with warning: "1 late trade approaching cutoff." Exception Queue shows FX-012300 with materiality classification and regulatory implications.
- **HITL gate:** Cannot auto-except (material). Ops must choose: expedite processing OR hold for next business day. Agent proposes `holdForNextDay(FX-012300)` (Risk M) → Ops approves after consulting compliance.
- **Outcome:** Trade held for T+1 processing. APAC EOD proceeds without it. Audit trail records decision.

### C3. Exception Approval for Immaterial Trades

- **Personas:** Operations Staff (Admin Portal), Risk Manager (Admin Portal)
- **Trigger:** AMERICAS region has 12 unresolved exceptions before global close. Agent performs bulk materiality assessment.
- **Agents invoked:**
  1. **Exception Materiality** — calls `getUnresolvedExceptions()`, `getExposure(tradeId)` for each, `classifyMateriality()`.
  2. Result: 10 immaterial (total $120K), 2 material ($4.8M combined).
- **Portal interactions:** Exception Queue shows pre-sorted list: 10 with green "IMMATERIAL" badge, 2 with red "MATERIAL" badge. Bulk approve button available for immaterial set.
- **HITL gate:** Bulk `approveException(AMERICAS, [10 trades])` (Risk M) → Ops approves. Material trades escalated to Risk Manager.
- **Outcome:** 10 exceptions cleared. AMERICAS progresses. 2 material exceptions remain for individual resolution.

### C4. Global Consolidation Trigger

- **Personas:** Operations Staff (Admin Portal), Risk Manager (Admin Portal)
- **Trigger:** All three regions (APAC, EMEA, AMERICAS) reach `READY` status on EOD Dashboard.
- **Agents invoked:**
  1. **EOD Readiness** — calls `getRegionalCloseStatus()` for all regions; confirms all READY.
  2. Calls `getRiskAggregationStatus()` — confirms all regional risk aggregations complete.
  3. Proposes: "All regions ready. Global consolidation can proceed."
- **Portal interactions:** EOD Dashboard shows all three regions green. "Start Global Consolidation" button becomes active. Ops clicks — confirmation dialog shows summary.
- **HITL gate:** `startGlobalConsolidation()` (Risk H) → Ops initiates, Risk Manager co-approves.
- **Outcome:** Global consolidation runs. EOD Dashboard updates to show `CONSOLIDATING` → `CLOSED`. Business day advances.

### C5. EOD Readiness Check Across All Regions

- **Personas:** Operations Staff (Admin Portal)
- **Trigger:** Scheduled pre-EOD check (30 minutes before first regional cutoff — APAC).
- **Agents invoked:**
  1. **EOD Readiness** — parallel calls: `getRegionalCloseStatus()`, `getUnprocessedTradeCount()`, `getMarketDataReadiness()`, `getBranchCompletionStatus()` for each region.
  2. Produces global readiness map: APAC: READY, EMEA: 3 trades pending (immaterial), AMERICAS: market data stale for CAD/MXN.
- **Portal interactions:** EOD Dashboard shows pre-flight status. APAC green, EMEA amber (minor), AMERICAS red (data dependency).
- **HITL gate:** None for the check itself (Risk L). Remediation actions trigger individual HITL gates.
- **Outcome:** Ops has 30-minute advance warning. Initiates market data investigation for AMERICAS (triggers D4).

### C6. Branch Completion Tracking

- **Personas:** Operations Staff (Admin Portal)
- **Trigger:** EMEA EOD in progress; 4 of 6 branches complete, 2 still processing.
- **Agents invoked:**
  1. **EOD Readiness** — calls `getBranchCompletionStatus(EMEA)`; returns per-branch status with trade counts and estimated completion times.
  2. Identifies: London branch complete, Frankfurt complete, Paris complete, Zurich complete; Milan (14 trades, est. 8 min), Dublin (3 trades, est. 2 min).
- **Portal interactions:** EOD Dashboard shows EMEA expanded view with branch-level progress bars. Milan highlighted as slowest.
- **HITL gate:** None (monitoring, Risk L).
- **Outcome:** Ops monitors passively. Both branches complete within estimates. EMEA transitions to READY.

### C7. EOD Rerun After Fix

- **Personas:** Operations Staff (Admin Portal), Risk Manager (Admin Portal)
- **Trigger:** EMEA EOD failed due to a blocker (stale market data for EUR/CHF). Data is now fresh. Ops requests rerun.
- **Agents invoked:**
  1. **EOD Readiness** — verifies blocker resolved: `getMarketDataReadiness(EMEA)` returns FRESH for all pairs.
  2. **Data Freshness** — confirms EUR/CHF feed recovered, last tick < 30 seconds ago.
  3. Proposes regional rerun.
- **Portal interactions:** EOD Dashboard shows EMEA in `BLOCKED` (stale data resolved). "Request Rerun" button active. Impact report shows: "22 trades pending recalculation with fresh EUR/CHF rates."
- **HITL gate:** `startRegionalRerun(EMEA)` (Risk H) → Ops requests, Risk Manager approves.
- **Outcome:** EMEA EOD reruns. 22 trades recalculated. Region transitions BLOCKED → IN_PROGRESS → READY.

---

## Category D: Event & Data Integrity

### D1. DLQ Triage — Transient Replay

- **Personas:** Operations Staff (Admin Portal)
- **Trigger:** Kafka trigger — 15 messages land on `trade-lifecycle.dlq` within 2 minutes.
- **Agents invoked:**
  1. **DLQ Triage** — calls `classifyDlqMessage()` and `groupFailuresBySignature()`; Python sidecar clusters stack traces.
  2. Result: 15 messages, 1 signature — all `ConnectionTimeoutException` to enrichment-service (transient; service recovered 30s ago).
- **Portal interactions:** Exception Queue shows "DLQ cluster: 15 messages, signature: ConnectionTimeout (transient)." Action button: "Replay All (transient)."
- **HITL gate:** `replayDlqMessage(batch)` (Risk M) → Ops reviews cluster analysis → approves batch replay.
- **Outcome:** 15 messages replayed successfully. DLQ drained. Trades resume processing.

### D2. DLQ Triage — Poison Quarantine

- **Personas:** Operations Staff (Admin Portal), Development/SRE (Kibana)
- **Trigger:** Kafka trigger — message for FX-015600 has failed 5 retries on `risk-calculation.dlq`.
- **Agents invoked:**
  1. **DLQ Triage** — calls `classifyDlqMessage()`; Python sidecar analyzes: `NullPointerException` in rule evaluation — not transient.
  2. Classification: POISON. Replay will not help.
- **Portal interactions:** Exception Queue shows FX-015600 with "POISON — requires manual review" label. No replay button. Links to ELK log correlation for the error.
- **HITL gate:** Agent proposes `quarantineMessage(FX-015600)` (Risk M) → Ops approves quarantine.
- **Outcome:** Message quarantined (moved to poison topic). SRE team investigates root cause in Kibana. Trade held until code fix deployed.

### D3. Consumer Lag Threatening Cutoff

- **Personas:** Operations Staff (Admin Portal), Platform Administrator (Admin Portal)
- **Trigger:** Python sidecar forecasts: `trade-lifecycle` consumer group will not finish processing before EMEA cutoff at current rate.
- **Agents invoked:**
  1. **Consumer Lag Predictor** — calls `getLagByPartition()`, `getCompletionForecast()`, `getHotPartitionKeys()`.
  2. Forecast: 84,000 messages remaining; at 1,200/sec, estimated 70 minutes; cutoff in 45 minutes. Hot partition: key=EUR/GBP (40% of volume).
- **Portal interactions:** Admin Portal shows alert: "Consumer lag will miss EMEA cutoff by 25 minutes." Scaling proposal displayed with cost/impact analysis.
- **HITL gate:** Agent proposes `requestReplicaScale(trade-lifecycle, 18→28)` (Risk H) → Platform Admin approves.
- **Outcome:** Consumer scaled. Processing rate increases to 2,100/sec. New estimate: 40 minutes. Cutoff met with 5 minutes margin.

### D4. Market Data Feed Stale

- **Personas:** Operations Staff (Admin Portal), Risk Manager (Admin Portal)
- **Trigger:** Python sidecar detects: EUR/CHF feed — no tick for 3 minutes (baseline: tick every 2-5 seconds).
- **Agents invoked:**
  1. **Market Data Staleness** — calls `getFeedFreshness(EUR/CHF)`, `detectCrossedQuote()`, `getStalePairs()`, `getDownstreamRiskDependency(EUR/CHF)`.
  2. Finds: 34 trades pending risk calculation depend on EUR/CHF. Feed provider: FX-FEED-PROVIDER-3 (other pairs from same provider are live).
- **Portal interactions:** Admin Portal shows stale feed alert with downstream impact: "34 trades blocked from risk calculation." Risk Aggregation View shows staleness timestamp for EUR/CHF-dependent books.
- **HITL gate:** Agent proposes `blockRiskCalc(EUR/CHF)` (Risk M) → Risk Manager approves (prevents calculation with stale data).
- **Outcome:** Risk calculations for EUR/CHF trades paused. When feed recovers (sidecar detects fresh tick), block auto-lifts and trades process.

### D5. Schema Breaking Change Detection

- **Personas:** Development/SRE (Grafana/Kibana), Platform Administrator (Admin Portal)
- **Trigger:** New schema version v3 registered for `trade-enriched-events` topic; compatibility check: BACKWARD_INCOMPATIBLE.
- **Agents invoked:**
  1. **Schema Drift** — calls `getSchemaCompatibility(trade-enriched-events, v3)`, `findConsumersOf(trade-enriched-events)`, `simulatePayloadAgainstConsumers()`.
  2. Finds: 3 consumers (risk-calculation, eod-processing, state-reconciliation). Risk-calculation will fail on removed field `originalCurrency`.
- **Portal interactions:** Admin Portal Exception Queue shows: "Breaking schema change — 3 consumers affected." SRE sees alert in Grafana dashboard.
- **HITL gate:** Agent flags as advisory: `flagBreakingChange()` (Risk M) — blocks schema promotion until consumers updated.
- **Outcome:** Schema v3 promotion blocked. SRE coordinates consumer updates. Schema promoted only after all consumers compatible.

### D6. Post-Cutoff Event Handling

- **Personas:** Operations Staff (Admin Portal)
- **Trigger:** Trade FX-018200 ingested 2 minutes after APAC cutoff (17:02 vs 17:00 cutoff).
- **Agents invoked:**
  1. **Cutoff Calendar** — calls `getRegionalCutoff(APAC)`, `classifyBookingDate(FX-018200)`.
  2. Determines: trade arrived post-cutoff. If booked today, it contaminates APAC close. Correct booking date: T+1.
- **Portal interactions:** Exception Queue shows: "Post-cutoff trade FX-018200 — booking date classification: T+1." Trade Investigation View shows cutoff boundary annotation on timeline.
- **HITL gate:** Agent proposes `holdForNextDay(FX-018200)` (Risk M) → Ops approves.
- **Outcome:** Trade held for next business day processing. APAC EOD unaffected. Trade processes normally on T+1.

### D7. Sequence Violation Alert

- **Personas:** Operations Staff (Admin Portal)
- **Trigger:** Kafka Streams processor emits sequence violation: FX-020100 received `BOOKED` before `RISK_CALCULATED`.
- **Agents invoked:**
  1. **Event Integrity** — calls `getSequenceFacts(FX-020100)`; confirms out-of-order: BOOKED arrived first, RISK_CALCULATED arrived 200ms later (Kafka partition rebalance caused reordering).
  2. Assessment: both events present, just misordered. No data loss.
- **Portal interactions:** Exception Queue shows sequence violation with explanation: "Reordering due to partition rebalance — not data loss." Low severity indicator.
- **HITL gate:** None if agent confirms benign reordering (Risk L). If data loss suspected, escalates to M.
- **Outcome:** Ops acknowledges benign reordering. Event Sequence Processor's state corrects on next compaction. No action needed.

### D8. Data Freshness Gate Before Processing

- **Personas:** Operations Staff (Admin Portal), Risk Manager (Admin Portal)
- **Trigger:** EOD risk aggregation about to start; Data Freshness agent performs pre-flight check.
- **Agents invoked:**
  1. **Data Freshness** — calls `getDatasetFreshness()`, `getCompleteness()`, `getAuthoritativeness()` for all input datasets.
  2. Result: trade-events: FRESH ✓, market-data: FRESH ✓, counterparty-static: STALE (last update 6 hours ago, policy max: 2 hours).
- **Portal interactions:** Admin Portal shows pre-processing gate result: 2/3 datasets pass, 1 blocked. Links to counterparty-static data source status.
- **HITL gate:** Policy returns `BLOCK`. Agent proposes: "Wait for counterparty-static refresh, or override with stale data." Override requires Risk Manager approval (Risk M).
- **Outcome:** Ops waits 10 minutes; static data refreshes. Gate passes. Aggregation proceeds with fresh data.

---

## Category E: Observability & Correlation

### E1. Business KPI Anomaly Investigation

- **Personas:** Operations Staff (Admin Portal), Development/SRE (Grafana)
- **Trigger:** Python KPI sidecar detects: APAC booking rate 41% below 5-day seasonal norm at 14:00 local time.
- **Agents invoked:**
  1. **Business KPI Guard** — calls `getBusinessKpis(APAC)`, `getSeasonalBaseline(bookingRate)`, `getRejectBreakdown()`.
  2. Identifies: risk-calculation service not emitting RISK_CALCULATED events for APAC region since 13:45. 67 trades queued.
  3. Routes to **Lifecycle Reconstruction** for service health check.
- **Portal interactions:** Admin Portal shows KPI alert: "APAC booking 41% below norm." Drill-down shows risk-service bottleneck. Grafana dashboard confirms request queue growing.
- **HITL gate:** None for investigation (Risk L). If remediation proposed, separate HITL gate applies.
- **Outcome:** Root cause identified: risk-service memory pressure causing GC pauses. SRE team scales the pod.

### E2. Deploy-to-Behavior Correlation

- **Personas:** Development/SRE (Grafana/Kibana), Operations Staff (Admin Portal)
- **Trigger:** Business rejection rate increases 14% starting at 14:05; Change Correlation agent activates.
- **Agents invoked:**
  1. **Change Correlation** — calls `getRecentChanges(window=2h)`, `correlateChangeToOutcome()`, `getChangeGraph()`.
  2. Timeline: rule package 7.14 deployed at 14:01 → EUR/GBP rejection spike at 14:05 → concentrated in book FX-BOOK-EMEA-17.
- **Portal interactions:** SRE sees in Grafana: deploy marker correlated with metric shift. Admin Portal shows agent explanation: "Rejection ↑14:05 ← rule pkg 7.14 @14:01 → EUR/GBP +28% → book B17."
- **HITL gate:** None for correlation (Risk L). Triggers B2 (Rule Deployment Impact) for remediation.
- **Outcome:** Causal chain established in < 2 minutes. Previously would require 30+ minutes of manual log correlation.

### E3. Distributed Trace Latency Root Cause

- **Personas:** Development/SRE (Jaeger), Operations Staff (Admin Portal)
- **Trigger:** Trade FX-025000 took 45 seconds end-to-end (SLA: 5 seconds). Trader reports slow processing.
- **Agents invoked:**
  1. **Trace Latency** — calls `getTradeTrace(FX-025000)`, `getSpanBreakdown()`, `getServiceBaseline(enrichment-service)`, `correlateToDeploy()`.
  2. Breakdown: ingestion 200ms ✓, validation 150ms ✓, enrichment 43,800ms ✗ (baseline: 400ms). Root cause: Redis `HGETALL` call to enrichment cache took 43s (cache miss + cold read from Postgres).
- **Portal interactions:** SRE views trace waterfall in Jaeger — enrichment span dominates. Admin Portal shows summary: "Enrichment stalled on cache miss → Postgres cold read."
- **HITL gate:** None (Risk L, diagnostic only).
- **Outcome:** SRE identifies Redis eviction policy as root cause. Cache warming job scheduled. No trades lost, just delayed.

### E4. Canary Probe Detects Stuck Pipeline

- **Personas:** Operations Staff (Admin Portal), Platform Administrator (Admin Portal)
- **Trigger:** Scheduled canary: synthetic trade FX-CANARY-EMEA-0847 injected at 10:00; expected to reach SETTLED within 90 seconds.
- **Agents invoked:**
  1. **Canary Probe** — calls `injectSyntheticTrade(EMEA, EUR/USD)`, waits, calls `traceSyntheticProgress()`, `assertExpectedLifecycle()`.
  2. Result: canary stuck at ENRICHED for 120 seconds. All infrastructure health checks pass (CPU, memory, network OK).
  3. Diagnosis: business logic stuck, not infrastructure — Drools rule evaluation hanging on new rule with infinite loop condition.
- **Portal interactions:** Admin Portal shows canary failure alert: "EMEA pipeline stuck at ENRICHED stage. Infrastructure healthy — business logic issue." Links to rule evaluation logs.
- **HITL gate:** Agent proposes `openBusinessDegradation(EMEA, enrichment)` (Risk M) → Ops approves degradation declaration.
- **Outcome:** Business degradation declared. Ops and SRE focus on rule engine issue, not infrastructure. Canary proved business liveness ≠ infra liveness.

### E5. Runtime Intent Classification (Suppress False Alarm)

- **Personas:** Operations Staff (Admin Portal), Development/SRE (Grafana)
- **Trigger:** Burst of activity at 16:55 — CPU spikes across 4 services, Kafka throughput +300%, Redis operations +500%.
- **Agents invoked:**
  1. **Runtime Intent** — calls `getRecentActivity(window=15m)`, `classifyIntent()`, `alignToBusinessGoal()`.
  2. Python sidecar clusters behavioral signals. Classification: "EOD processing ramp for AMERICAS region — expected daily pattern."
  3. Aligns to business goal: AMERICAS EOD starts at 17:00; pre-processing ramp is normal.
- **Portal interactions:** Admin Portal shows: "Activity burst classified as: EOD pre-processing ramp (AMERICAS). No incident." Grafana annotation added: "Normal EOD ramp."
- **HITL gate:** None (Risk L).
- **Outcome:** False alarm suppressed. On-call SRE not paged. Alert fatigue reduced.

### E6. Service Genome Fragility Prediction

- **Personas:** Development/SRE (Grafana), Platform Administrator (Admin Portal)
- **Trigger:** Pre-change analysis — SRE plans to deploy new version of enrichment-service. Requests fragility assessment.
- **Agents invoked:**
  1. **Service Genome** — calls `getServiceProfile(enrichment-service)`, `getDependencies()`, `predictFragility()`, `findConsumersOfChange()`.
  2. Python sidecar analyzes: service has 7 downstream consumers, touches 4 Kafka topics, shares Redis cache with risk-calculation.
  3. Fragility score: HIGH. Risk: shared Redis cache eviction pattern change could cascade to risk-service.
- **Portal interactions:** Admin Portal shows service genome report: dependency graph visualization, fragility score, blast radius preview. Grafana shows historical correlation: last 3 enrichment deploys caused 2 incidents.
- **HITL gate:** None (Risk L, advisory). Influences deploy decision.
- **Outcome:** SRE adds canary deployment strategy and pre-deploys Redis cache warming. Reduces blast radius before change.

### E7. Contagion Blast Radius Analysis

- **Personas:** Operations Staff (Admin Portal), Risk Manager (Admin Portal)
- **Trigger:** Market data feed FX-FEED-PROVIDER-3 goes down. Ops asks: "What's the blast radius?"
- **Agents invoked:**
  1. **Contagion Analysis** — calls `findSharedMarketDataDependencies(FX-FEED-PROVIDER-3)`, `findAffectedBooks()`, `findDownstreamAggregations()`, `calculateBusinessBlastRadius()`.
  2. Neo4j traversal reveals: 6 currency pairs affected → 142 active trades → 3 trading books → EMEA and AMERICAS regions → 2 EOD aggregations.
- **Portal interactions:** Admin Portal shows blast radius report: "Feed outage affects 142 trades across 3 books. EMEA EOD at risk if not resolved in 20 minutes."
- **HITL gate:** None for analysis (Risk L). Downstream actions (block risk calc, escalate) have their own gates.
- **Outcome:** Ops and Risk Manager have full impact picture in 30 seconds. Previously required manual Neo4j queries and cross-referencing 3 dashboards.

---

## Category F: Capacity, Cost & Recovery

### F1. Capacity Backlog vs Deadline — Scaling Proposal

- **Personas:** Platform Administrator (Admin Portal), Operations Staff (Admin Portal)
- **Trigger:** Python capacity sidecar projects: 2.4M message backlog; at current rate (18 replicas), estimated completion 47 minutes; EMEA cutoff in 31 minutes.
- **Agents invoked:**
  1. **Capacity Backlog** — calls `getBacklog(EMEA)`, `getCompletionEstimate()`, `proposeScalingPlan()`.
  2. Scaling plan: +8 replicas (18→26) reduces estimate to 27 minutes. Cost: $12/hour additional. Alternative: defer non-critical reconciliation (-400K messages, estimate drops to 29 minutes without scaling).
- **Portal interactions:** Admin Portal shows: backlog gauge, countdown to cutoff, two proposals with cost/time comparison. Platform Admin selects scaling option.
- **HITL gate:** `applyScalingPlan(trade-lifecycle, 18→26)` (Risk H) → Platform Admin approves.
- **Outcome:** Replicas scaled. Processing rate increases. EMEA cutoff met with 4 minutes margin. Scale-down scheduled for 30 minutes after cutoff.

### F2. Retry Storm — Backpressure Coordination

- **Personas:** Platform Administrator (Admin Portal), Development/SRE (Grafana)
- **Trigger:** Python sidecar detects retry amplification: enrichment-service retry rate 40x baseline; 3 circuit breakers open downstream.
- **Agents invoked:**
  1. **Retry Storm** — calls `getRetryAmplification()`, `getOpenBreakers()`, `getCascadePath(enrichment-service)`.
  2. Root cause: counterparty-static-service is down (the actual failure). Enrichment retries cascade to risk-calculation (via shared connection pool) and settlement-service (via event replay).
  3. Correct action: shed load at enrichment (the source), not at risk-calculation (the symptom).
- **Portal interactions:** Admin Portal shows cascade diagram: counterparty-static ✗ → enrichment retries → risk-calc overwhelmed → settlement backlog. Correct intervention point highlighted.
- **HITL gate:** Agent proposes `applyBackpressure(enrichment-service)` + `tripBreaker(enrichment→counterparty-static)` (Risk H) → Platform Admin approves.
- **Outcome:** Backpressure applied at correct point. Retry storm stops within 30 seconds. Downstream services recover. When counterparty-static recovers, breaker resets and processing resumes.

### F3. FinOps Cost Spike Investigation

- **Personas:** Platform Administrator (Admin Portal), Development/SRE (Grafana)
- **Trigger:** Python cost sidecar detects: compute cost +180% week-over-week despite flat FX volume.
- **Agents invoked:**
  1. **FinOps Cost** — calls `getCostByService()`, `correlateCostToDeploy()`, `getIdleCapacity()`.
  2. Root cause: rule package 7.14 (deployed Tuesday) doubled risk-engine invocations per trade (evaluating same rule twice due to misconfigured condition). Cost increase isolated to risk-calculation service EKS pods.
- **Portal interactions:** Admin Portal shows cost breakdown: risk-calculation +210%, other services flat. Correlation: "Rule 7.14 deployment → 2x risk calls per trade." Idle capacity analysis: "4 pods running at 15% utilization post-EOD."
- **HITL gate:** Agent proposes `proposeRightsizing()` for post-EOD hours (Risk H) → Platform Admin approves scale-down schedule.
- **Outcome:** Rule fix deployed (eliminates double-evaluation). Post-EOD auto-scale-down configured. Projected savings: $2,400/week.

### F4. Adaptive Routing During Degradation

- **Personas:** Operations Staff (Admin Portal), Platform Administrator (Admin Portal)
- **Trigger:** Risk-calculation service for EMEA region degraded (p99 latency 12s vs baseline 800ms). Other regions healthy.
- **Agents invoked:**
  1. **Adaptive Routing** — calls `getRuntimeConditions()`, `proposeRoutingPolicy()`, `validateRoutingPolicy()` (rules service validates policy won't violate regulatory constraints).
  2. Proposal: temporarily route EMEA EUR/USD and EUR/GBP trades (highest volume) to AMERICAS risk-calculation pool (within regulatory boundary). Keep exotic pairs on EMEA (low volume, won't overload).
- **Portal interactions:** Admin Portal shows routing proposal with impact: "Route 60% of EMEA volume to AMERICAS. AMERICAS capacity headroom: 40%. No regulatory violations."
- **HITL gate:** `applyRoutingConfig(EMEA→AMERICAS, pairs=[EUR/USD, EUR/GBP])` (Risk H) → Ops + Platform Admin approve.
- **Outcome:** Routing policy applied. EMEA major pairs process at normal latency via AMERICAS. Exotic pairs process slowly but within SLA. Policy auto-expires in 2 hours.

### F5. Transaction Recovery — Full Multi-Phase

- **Personas:** Operations Staff (Admin Portal), Risk Manager (Admin Portal)
- **Trigger:** Trade FX-030100 stuck in `SETTLEMENT_INSTRUCTED` for 4 hours. Investigation (A1) confirms: settlement gateway acknowledged but no confirmation received; gateway is now healthy.
- **Agents invoked:**
  1. **Transaction Recovery Coordinator** — multi-agent phase:
     - **Investigation** — `verifyNoSettlement()` confirms no funds moved.
     - **Planning** — generates 5-step plan: check idempotency → invalidate stale cache → replay settlement instruction → verify gateway response → compare final state.
     - **Safety** — validates each step is reversible and idempotent.
     - **Execution** — executes steps sequentially with verification gates.
     - **Audit** — records each step with before/after state.
- **Portal interactions:** Ops sees recovery plan (5 steps) in Approval Widget. Progress indicator updates as each step completes. Final state comparison shown.
- **HITL gate:** Full plan approval (Risk H) → Ops approves. Each step reports status; if any step fails, execution halts and re-proposes.
- **Outcome:** Settlement instruction replayed. Gateway confirms. Trade advances to `SETTLED`. Audit trail complete.

### F6. Duplicate Settlement Reversal

- **Personas:** Operations Staff (Admin Portal), Risk Manager (Admin Portal)
- **Trigger:** Duplicate Effect Guard detects: FX-031500 has two `SETTLEMENT_CONFIRMED` events — $2.5M paid twice to counterparty FX-CP-ASIA-12.
- **Agents invoked:**
  1. **Duplicate Effect Guard** — calls `findDoubleBooking(FX-031500)`, `findDuplicateSettlementInstruction()`; confirms real financial double-effect (not benign retry).
  2. **Transaction Recovery Coordinator** — generates reversal plan: initiate recall for duplicate payment, update settlement ledger, notify counterparty.
- **Portal interactions:** Admin Portal shows critical alert: "Double settlement — $2.5M duplicate to FX-CP-ASIA-12." Dry-run reversal report shows exact amounts and counterparty details.
- **HITL gate:** `reverseDuplicateEffect(FX-031500, SI-4477)` (Risk H) → Ops reviews dry-run → Risk Manager co-approves.
- **Outcome:** Recall initiated. Counterparty notified. Settlement ledger corrected. Audit trail records entire sequence.

### F7. Settlement Fail Prediction and Prevention

- **Personas:** Broker (FX Blotter), Operations Staff (Admin Portal)
- **Trigger:** Pre-settlement sweep (T-2 hours before settlement window). Python ML model flags 3 trades at high fail probability.
- **Agents invoked:**
  1. **Settlement Fail Prediction** — calls `getMissingSSI(FX-032001)`, `getNostroShortfall(JPY)`, `predictFailProbability()` for flagged trades.
  2. Results: FX-032001 — missing SSI for counterparty (95% fail probability). FX-032002 — JPY nostro shortfall of ¥50M (80% fail). FX-032003 — counterparty historically fails Friday settlements (70% fail).
- **Portal interactions:** Broker sees at-risk indicator on Settlement Status View for 3 trades. Ops sees prioritized list: "3 trades at settlement risk" with reasons and prevention actions.
- **HITL gate:** Agent proposes `escalateSettlementRisk(FX-032001)` to request SSI from counterparty (Risk H) → Ops approves escalation.
- **Outcome:** SSI requested and received before cutoff. Nostro funded via treasury. 2 of 3 trades settle successfully; third (Friday pattern) monitored closely.

---

## Category G: Multi-Agent & Supervisor

### G1. Multi-Turn Conversation (Supervisor Memory)

- **Personas:** Operations Staff (Admin Portal)
- **Trigger:** Ops asks supervisor: "What happened to FX-040100?" → gets lifecycle answer → follows up: "Why was its risk so high?" → follows up: "Is that the same issue affecting other EUR/GBP trades today?"
- **Agents invoked:**
  1. **Supervisor** (turn 1) — classifies intent: lifecycle query → routes to **Lifecycle Reconstruction** → returns timeline.
  2. **Supervisor** (turn 2) — recognizes same trade context from session memory → routes to **Risk Explainability** → returns factor breakdown.
  3. **Supervisor** (turn 3) — infers broader scope from context → routes to **Business KPI Guard** with filter EUR/GBP → returns: "Yes, 14 EUR/GBP trades showing elevated risk due to volatility spike at 09:30."
- **Portal interactions:** Ops interacts through conversational interface in Admin Portal. Each response builds on prior context without re-stating trade ID or pair.
- **HITL gate:** None (all queries are Risk L).
- **Outcome:** Ops gets progressively deeper understanding across 3 turns without restarting context. Session memory maintained in Redis.

### G2. Cross-Agent Investigation (Supervisor Routes to Multiple)

- **Personas:** Operations Staff (Admin Portal), Risk Manager (Admin Portal)
- **Trigger:** Ops asks: "EMEA is running slow — what's wrong and can we still close on time?"
- **Agents invoked:**
  1. **Supervisor** — decomposes into 3 parallel sub-queries:
     - **EOD Readiness** — regional status, unprocessed count, branch completion.
     - **Consumer Lag Predictor** — processing rate vs cutoff deadline.
     - **Business KPI Guard** — EMEA KPIs vs baseline (booking rate, risk calc rate).
  2. **Supervisor** synthesizes: "EMEA 23% slower due to risk-service GC pauses. 1,200 trades pending. At current rate, will miss cutoff by 8 minutes. Scaling by +4 replicas would close the gap."
- **Portal interactions:** Admin Portal shows unified response combining: EOD status (EMEA IN_PROGRESS), lag forecast chart, KPI deviation, and proposed remediation.
- **HITL gate:** Scaling proposal requires Platform Admin approval (Risk H). EOD status itself is Risk L.
- **Outcome:** Single question triggers 3 agents in parallel; supervisor produces unified actionable answer with embedded remediation option.

### G3. Follow-Up Question After Initial Investigation

- **Personas:** Risk Manager (Admin Portal)
- **Trigger:** Risk Manager approved a rule rollback (B2). 10 minutes later asks: "Did the rollback fix the rejection rate?"
- **Agents invoked:**
  1. **Supervisor** — references session memory: knows rule v7.14 was rolled back to v7.13, context is EUR/GBP rejections.
  2. Routes to **Business KPI Guard** — calls `getBusinessKpis(EMEA)` with focus on rejection rate post-rollback.
  3. Also routes to **Rule Impact** — calls `getRuleFiringStats()` for v7.13 (current, rolled-back-to version).
  4. Synthesizes: "Rejection rate returned to baseline within 4 minutes of rollback. 142 previously blocked trades reprocessed — 138 passed, 4 correctly rejected by other rules."
- **Portal interactions:** Risk Manager sees confirmation in Admin Portal with before/after chart: rejection rate spike and recovery. Links to the 4 trades still correctly rejected.
- **HITL gate:** None (Risk L, monitoring query).
- **Outcome:** Risk Manager confirms remediation was effective without running separate queries. Complete feedback loop from detection → action → verification.

---

## Summary

| Category | Use Cases | Risk Levels | Primary Personas |
|----------|-----------|-------------|-----------------|
| A: Trade Lifecycle | 10 | L–H | Operations Staff, FX Trader |
| B: Risk & Rules | 8 | L–H | Risk Manager, Rules Owner, FX Trader |
| C: End-of-Day | 7 | M–H | Operations Staff, Risk Manager |
| D: Event & Data | 8 | L–M | Operations Staff, Platform Admin, SRE |
| E: Observability | 7 | L–M | SRE, Operations Staff |
| F: Capacity & Recovery | 7 | H | Platform Admin, Operations Staff, Broker |
| G: Multi-Agent | 3 | L (inherits) | Operations Staff, Risk Manager |
| **Total** | **50** | | |

> All identifiers (FX-000001, FX-CP-NORDIC-7, FX-BOOK-EMEA-03, etc.) are synthetic. No real financial data.
