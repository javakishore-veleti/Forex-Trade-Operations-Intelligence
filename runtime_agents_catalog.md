# Runtime Intelligence Agents — Master Catalog

> **Public reference implementation notice**
> Generalized architecture patterns and fictional examples for an open-source reference implementation. All trade IDs, rule versions, regions, services, and scenarios are illustrative and represent no employer, client, or production system. See `forex_trade_operations_intelligence_public.md` for the framework this catalog builds on.

This catalog consolidates **34 deduplicated agent concepts** gathered from four AI sources (ChatGPT, Claude, Copilot, Gemini) into one build backlog. It is a companion to the design document, which supplies the shared framework (model portfolio, tool-envelope contract, "where n8n fits").

## Standing architecture (applies to every agent)

```
User / Event
   │
n8n  (agentic layer — MCP client / agent host: routing, memory, reflection, human-in-the-loop)
   │  discovers + calls
Spring AI MCP tools  (typed tool boundary — inputSchema = action payload; structured output = fact envelope)
   │
Spring Boot business logic  (Java/Maven — ALL trade/risk/EOD/rules/transactional logic)
   │
Kafka/EventHub · Postgres · MongoDB · Redis · Neo4j · Drools · Databricks · ELK · Grafana
   ▲
Python sidecars (detection models, embeddings) — emit compact anomaly envelopes as triggers; NEVER business logic
```

**Non-negotiables** (from the design doc):
- **Spring Boot only** for microservices/tools; **Python only** for the agentic/AI sidecar layer.
- **LLMs never compute official numbers** — risk, exposure, materiality, canonical state come from deterministic services.
- **High-volume Kafka never flows through an LLM** — stream processors detect, then trigger n8n with an envelope.
- **Every action gates behind human approval**: `propose → deterministic simulation → impact report → human approval → controlled MCP tool applies`. Any rival "autonomous" action (auto-freeze, auto-scale, auto-adjust thresholds) is rewritten to this pattern here.

## How to read a card

- **Source tag:** `[GPT]` ChatGPT · `[CLA]` Claude · `[COP]` Copilot · `[GEM]` Gemini
- **Risk:** **L** read/explain only · **M** blocks/quarantines/holds a process · **H** moves money/state/config (always human-gated)
- **Model tiers (current Claude):** Perception/extraction/structured-JSON → **Haiku 4.5** · mid → **Sonnet 5** · reasoning/planning → **Opus 4.8** · Detection → deterministic Python/Spark/SQL (no LLM) · Embedding → embedding model · Policy → Drools/deterministic
- **Patterns:** references the 20 agentic design patterns (index at the end)

---

## Theme 1 — Trade Lifecycle & State Integrity

### 1. Global Trade Lifecycle Reconstruction `[GPT]` · Risk L
- **Trigger:** on-demand ("what happened to trade X?") / investigation request
- **Sources:** Kafka trade topics, EventHub, Postgres trade tables, Mongo docs, Redis state, ELK, actuator, audit DB, calendar
- **MCP tools:** `getTrade()`, `getTradeEvents()`, `getTradeAuditHistory()`, `getServiceProcessingStatus()`, `getExpectedLifecycle()`, `getBusinessCalendar()`; *(gated)* `replayTradeEvent()`, `requestTradeReprocessing()`
- **Python sidecar:** log/payload normalizer (perception)
- **Models:** Perception=Haiku · Reasoning=Opus · Embedding=similar prior failures · Memory
- **Patterns:** 1 Chaining · 5 Tool · 8 Memory · 13 RAG · 16 Reasoning · 12 HITL
- **Output:** timeline (observed/missing events) + probable cause + recommended safe action

### 2. Business-State Divergence `[GPT]` · Risk M
- **Trigger:** on-demand or scheduled cross-system sweep
- **Sources:** Postgres, Mongo, Redis, Kafka latest event, Databricks, rules service
- **MCP tools:** `queryTradeState()`, `getTradeDocument()`, `getCachedTradeState()`, `getLatestDomainEvent()`, `getAnalyticsTradeState()`, `evaluateCanonicalState()`; *(gated)* `startReconciliation()`
- **Deterministic:** `StateReconciliationService` returns `{states, expectedState, violatedInvariants, permittedActions}` — LLM interprets, never decides authoritative state
- **Models:** Detection=deterministic · Reasoning=Opus · Policy · Memory
- **Patterns:** 3 Parallelization · 5 Tool · 12 HITL · 19 Prioritization
- **Output:** divergence map + which source is stale + business impact + approved reconciliation

### 3. Event Integrity & Business Sequence `[GPT]` · Risk M
- **Trigger:** Kafka Streams processor emits a sequence-violation envelope
- **Sources:** Kafka Streams sequence facts, schema registry
- **MCP tools:** `getSequenceFacts(tradeId)`; *(gated)* `quarantineEvent()`, `pauseTradeProcessing()`, `requestReplay()`, `createReconciliationCase()`
- **Spring Boot:** Kafka Streams processor maintains `{observedEvents, missingEvents, duplicates, sequenceViolations}`
- **Models:** Detection=Kafka Streams (no LLM) · Reasoning=Opus · Planning · Memory
- **Patterns:** 5 Tool · 11 Exception · 12 HITL · 19 Prioritization
- **Output:** violation summary + safe action (quarantine / pause one trade / replay)

### 18. Duplicate Business-Effect Guard `[CLA]` · Risk H
- **Trigger:** replay executed, or two events share an idempotency key with divergent payloads
- **Sources:** Postgres trade/settlement, Redis idempotency store, Kafka log, audit
- **MCP tools:** `checkIdempotencyConsumed(key)`, `findDoubleBooking(tradeId)`, `findDuplicateSettlementInstruction()`; *(gated, dry-run first)* `reverseDuplicateEffect()`
- **Models:** Detection=deterministic effect-diff · Reasoning=Opus (real money vs benign retry) · Policy · Memory
- **Patterns:** 5 Tool · 11 Exception · 12 HITL · 18 Guardrails
- **Output:** "double settlement on FX-X → reversal proposed (dry-run), await approval"
- *Note: distinct from #3 — this checks financial double-effect, not event order.*

### 23. Trade Amendment / Cancellation Ripple `[CLA]` · Risk M
- **Trigger:** `TradeAmended` / `TradeCancelled` event
- **Sources:** Postgres, Mongo, Kafka, Neo4j (trade→book→region→report), reporting lake, settlement
- **MCP tools:** `findDownstreamEffects(tradeId)` (Cypher template), `checkRiskRecalcTriggered()`, `checkSettlementWithdrawn()`, `checkReportAmended()`; *(gated)* `requestMissingRecalc()`
- **Models:** Perception=Haiku · Reasoning=Opus · Planning=Opus · Policy · Memory
- **Patterns:** 5 Tool · 6 Planning · 12 HITL · 14 Inter-agent
- **Output:** which downstream steps didn't fire + corrective sequence

---

## Theme 2 — Risk & Rules

### 4. Trade Risk Explainability `[GPT]` · Risk L
- **Trigger:** user question ("why did this trade's risk increase?")
- **Sources:** trade chars, currency pair, Drools rule+version, market snapshot, region, book, counterparty class, prior/current risk, calendar, limits
- **MCP tools:** `getRiskResult(tradeId)` (returns `riskResult/previousRiskResult/contributingFactors/rulesFired`), `getRuleTrace()`, `getMarketSnapshot()`, `getLimitConfig()`
- **Deterministic:** risk service computes all official numbers
- **Models:** Reasoning=Opus · Perception=Haiku (rule trace→readable) · Embedding=rule docs/prior cases · Time-series=market-deviation detector (Python)
- **Patterns:** 5 Tool · 8 Memory · 13 RAG · 16 Reasoning
- **Output:** traceable multi-factor explanation; answers follow-ups

### 6. Runtime Business Rule Impact `[GPT]` · Risk H
- **Trigger:** post-deployment firing-pattern anomaly
- **Sources:** Drools audit, rule repo, version metadata, decision outcomes, Kafka, Databricks, deploy history
- **MCP tools:** `getRuleFiringStats()`, `compareRuleBehavior(preVer, postVer)`, `findConflictingRules()`, `simulateRule()`; *(gated)* `requestRuleRollback()`
- **Python sidecar:** firing-rate anomaly detector
- **Models:** Detection=Python stats · Reasoning=Opus · Embedding=similar rules/defects · Policy=Drools sim
- **Patterns:** 5 Tool · 10 Goal · 12 HITL · 16 Reasoning
- **Output:** "rule over-rejecting EUR crosses since v7.14 → rollback recommended"

### 25. Currency-Pair Rule-Coverage & Fallback-Firing `[CLA]` · Risk L
- **Trigger:** fallback-firing-rate baseline breach; new pair traded with no specific rule
- **Sources:** Drools audit, rule repo, decision outcomes, currency-pair master
- **MCP tools:** `getRuleCoverageMatrix()`, `getFallbackFiringRate(pair)`, `getUncoveredPairs()`, `simulateRuleGap()`
- **Models:** Detection=Python (firing-rate anomaly) · Reasoning=Opus · Policy=Drools sim · Memory
- **Patterns:** 5 Tool · 10 Goal · 20 Exploration
- **Output:** exotic pair with no specific rule → default mispricing risk → propose rule authoring

### 34. Shadow Rule Simulator `[GEM]` · Risk H (shadow-only, gated deploy)
- **Trigger:** analyst rule request (ticket) or NL request
- **Sources:** current DRL repo (Git), historical FX events (Kafka), Redis sim results, DTO/API schemas (vector)
- **MCP tools (shadow Spring Boot pod):** `loadShadowRule(drl)`, `replayHistoricalEvents(window)`, `readShadowRiskResults()`, `diffAgainstProduction()`
- **Scope note:** DRL parse/validate/simulate = Drools = **Spring Boot**, NOT Python. Python only for NL→DRL prompt glue + DRL-corpus embeddings.
- **Models:** Reasoning/Codegen=Opus (NL→DRL) · Embedding=DRL corpus · Detection=deterministic diff · Policy
- **Patterns:** 4 Reflection · 5 Tool · 6 Planning · 12 HITL · 17 Eval · 18 Guardrails (sandbox)
- **Output:** NL rule → DRL → shadow replay → impact diff → human-approved prod deploy
- *This is the SAFE version of Copilot's "auto-adjust thresholds": never touches production.*

### 13. Counterparty Exposure Narrative `[GPT]` · Risk L
- **Trigger:** on-demand for risk managers / scheduled EOD
- **Sources:** counterparty trades, exposure, limits, geo exposure, ccy-pair & book concentration, collateral, prior-day, exceptions
- **MCP tools:** `getCounterpartyExposure(cp)`, `getLimits(cp)`, `getConcentration(cp)`, `getCollateral(cp)`, `getPriorDayExposure(cp)`
- **Deterministic:** SQL/aggregation service + Neo4j graph traversal (no model arithmetic)
- **Models:** Reasoning=Opus · Retrieval=policies · Summarization=Haiku
- **Patterns:** 3 Parallelization · 5 Tool · 13 RAG · 16 Reasoning
- **Output:** live traceable exposure story: what changed, why, material?, action

---

## Theme 3 — End-of-Day, Readiness & Data Suitability

### 5. End-of-Day Risk Readiness `[GPT]` · Risk M
- **Trigger:** continuous during EOD window + on-demand
- **Sources:** regional close status, unprocessed counts, late-trade materiality, market-data readiness, branch completion, risk-aggregation status
- **MCP tools:** `getRegionalCloseStatus(region)`, `getUnprocessedTradeCount(region)`, `getLateTradeMateriality(region)`, `getMarketDataReadiness(region)`, `getBranchCompletionStatus(region)`, `getRiskAggregationStatus(region)`; *(gated)* `startRegionalRerun(region)`, `approveException(region)`, `startGlobalConsolidation()`
- **Models:** regional agents (Sonnet) + global supervisor (Opus) · Detection=deterministic · Policy
- **Patterns:** 3 Parallelization · 6 Planning · 7 Multi-agent · 10 Goal · 12 HITL
- **Output:** global readiness map (APAC ready / EMEA blocked / …) + go/no-go

### 10. Data Freshness & Decision-Suitability `[GPT]` · Risk M
- **Trigger:** before a critical process runs
- **Sources:** data catalog, Databricks tables, market-data, DQ results, schema registry, lineage, source timestamps, criticality policy
- **MCP tools:** `getDatasetFreshness(ds)`, `getCompleteness(ds)`, `getAuthoritativeness(ds)`; Policy service returns `BLOCK/ACCEPT`
- **Models:** Detection=deterministic · Reasoning=Opus (context) · Policy=deterministic
- **Patterns:** 5 Tool · 10 Goal · 12 HITL · 18 Guardrails
- **Output:** per-dataset freshness vs max → BLOCK/ACCEPT with impact

### 12. Exception Materiality `[GPT]` · Risk M
- **Trigger:** unresolved-exception set before global close
- **Sources:** exception store, exposure/notional, book status, materiality policy, regulatory implications
- **MCP tools:** `getUnresolvedExceptions()`, `getExposure(tradeId)`, `classifyMateriality()` (Drools)
- **Models:** Policy=Drools/risk (materiality) · Reasoning=Opus (explain) · Prioritization
- **Patterns:** 5 Tool · 12 HITL · 19 Prioritization
- **Output:** non-material vs blockers split + approval package

### 24. Settlement-Fail Prediction `[CLA]` · Risk H
- **Trigger:** pre-settlement window sweep
- **Sources:** standing settlement instructions, nostro balances, counterparty static, cutoff calendar, prior fails
- **MCP tools:** `getMissingSSI(tradeId)`, `getNostroShortfall(ccy)`, `predictFailProbability()`; *(gated)* `escalateSettlementRisk()`
- **Python sidecar:** fail-probability ML model
- **Models:** Detection=Python ML · Reasoning=Opus · Policy · Memory
- **Patterns:** 5 Tool · 10 Goal · 12 HITL · 19 Prioritization
- **Output:** prioritized at-risk settlements w/ reason (missing SSI + JPY shortfall)

### 29. Databricks Lineage & Freshness Impact `[CLA]` · Risk M
- **Trigger:** Databricks job failure/schema change; pre-aggregation gate
- **Sources:** Unity Catalog lineage, job history, schema registry, EOD dependency map
- **MCP tools:** `getLineageDownstream(table)`, `getJobStatus()`, `getAggregationReadiness(region)`; *(gated)* `blockAggregation()`
- **Python sidecar:** Databricks SDK ingestion
- **Models:** Perception=Haiku · Detection=deterministic · Reasoning=Opus · Policy
- **Patterns:** 5 Tool · 10 Goal · 12 HITL · 18 Guardrails
- **Output:** source late → EMEA aggregation contaminated → block

### 30. Regulatory-Reporting Completeness `[CLA]` · Risk M
- **Trigger:** pre-reporting-deadline sweep
- **Sources:** reportable-trade universe (Postgres), submitted-report ledger, reference data, reporting cutoffs
- **MCP tools:** `getReportableUniverse()`, `getSubmittedReports()`, `findReportingGaps()`, `getFieldValidationFailures()`; *(gated)* `resubmitReport()`
- **Models:** Detection=deterministic diff · Reasoning=Opus (why gap) · Policy · Memory
- **Patterns:** 5 Tool · 12 HITL · 17 Eval · 19 Prioritization
- **Output:** completeness attestation + gap fixes *(synthetic regimes only)*

---

## Theme 4 — Event & Data-in-Motion Integrity

### 17. DLQ Triage & Remediation `[CLA]` · Risk M
- **Trigger:** message on any dead-letter topic; batch/Kafka trigger
- **Sources:** DLQ topics + headers, error logs (ELK), schema registry, actuator
- **MCP tools:** `classifyDlqMessage()`, `groupFailuresBySignature()`; *(gated)* `replayDlqMessage(key)`, `quarantineMessage()`
- **Python sidecar:** stack-trace clustering + embeddings
- **Models:** Perception=Haiku · Detection=Python cluster · Reasoning=Opus · Memory
- **Patterns:** 5 Tool · 11 Exception · 12 HITL · 19 Prioritization · 20 Exploration
- **Output:** N msgs → K signatures → transient auto-replay / poison quarantine / hold

### 19. Consumer-Lag SLA Predictor & Partition-Skew `[CLA]` · Risk H
- **Trigger:** lag threshold or scheduled pre-cutoff check
- **Sources:** consumer-group offsets, partition assignment, per-partition throughput, cutoff calendar, historical completion curves
- **MCP tools:** `getLagByPartition()`, `getCompletionForecast()`, `getHotPartitionKeys()`; *(gated)* `requestReplicaScale(from,to)`
- **Python sidecar:** completion-time forecaster
- **Models:** Detection=Python forecast · Reasoning=Opus · Planning · Memory
- **Patterns:** 5 Tool · 6 Planning · 10 Goal · 12 HITL · 15 Resource-aware
- **Output:** "won't finish before EMEA cutoff; scale 18→26, defer reconciliation"

### 20. Schema & Contract Drift Compatibility `[CLA]` · Risk M
- **Trigger:** new schema version registered / OpenAPI spec deployed
- **Sources:** schema registry, OpenAPI/AsyncAPI, consumer registry, deploy history, flags
- **MCP tools:** `getSchemaCompatibility(subject,ver)`, `findConsumersOf(topic)`, `simulatePayloadAgainstConsumers()`; *(advisory)* `flagBreakingChange()`
- **Models:** Perception=Haiku (semantic diff) · Detection=rules · Reasoning=Opus · Policy · Memory
- **Patterns:** 5 Tool · 12 HITL · 14 Inter-agent · 18 Guardrails
- **Output:** who breaks + which business flow degrades

### 21. Market-Data Feed Staleness & Crossed-Quote `[CLA]` · Risk M
- **Trigger:** tick gap / crossed quote / stale pair / pre-calc probe
- **Sources:** FX rate feed(s), tick timestamps, Redis rate cache, downstream risk-request volume
- **MCP tools:** `getFeedFreshness(pair)`, `detectCrossedQuote()`, `getStalePairs()`, `getDownstreamRiskDependency(pair)`; *(gated)* `blockRiskCalc(pair)`
- **Python sidecar:** tick-gap statistics
- **Models:** Detection=Python/deterministic · Reasoning=Opus · Policy · Memory
- **Patterns:** 5 Tool · 10 Goal · 12 HITL · 18 Guardrails
- **Output:** stale/crossed pair → block EOD risk + list affected trades

### 22. Cutoff & Business-Calendar Enforcement `[CLA]` · Risk M
- **Trigger:** trade/event near or after a regional cutoff; per-region watcher
- **Sources:** business-calendar service, cutoff config, trade & ingestion timestamps
- **MCP tools:** `getRegionalCutoff(region)`, `getTradesApproachingCutoff()`, `getPostCutoffEvents()`, `classifyBookingDate()`; *(gated)* `holdForNextDay()`
- **Deterministic:** Spring Boot calendar math (exact — DST/holidays)
- **Models:** Detection=deterministic · Reasoning=Opus · Policy · Memory
- **Patterns:** 5 Tool · 10 Goal · 12 HITL
- **Output:** late trade lands on wrong business day → hold/flag

### 28. Retry-Storm / Backpressure Coordination `[CLA]` · Risk H
- **Trigger:** retry-rate amplification / breaker-open cascade
- **Sources:** Resilience4j metrics, retry counters, Grafana, dependency graph (Neo4j)
- **MCP tools:** `getRetryAmplification()`, `getOpenBreakers()`, `getCascadePath(service)`; *(gated)* `applyBackpressure()`, `tripBreaker()`
- **Python sidecar:** cascade graph analysis
- **Models:** Detection=Python · Reasoning=Opus (root vs symptom) · Planning · Memory
- **Patterns:** 5 Tool · 11 Exception · 12 HITL · 15 Resource-aware
- **Output:** root service identified → shed load here, not there

---

## Theme 5 — Recovery & Routing (action-heavy)

### 7. Transaction Recovery Coordinator `[GPT]` · Risk H
- **Trigger:** investigation concludes a trade is stuck / recovery requested
- **Sources:** state stores, idempotency store, event log, audit
- **MCP tools:** `verifyNoSettlement()`, `checkReplayKey()`, `invalidateCache()`, `replayEvent()`, `compareState()`, `closeRecoveryCase()` — all narrow, idempotent, dry-run capable
- **Models:** multi-agent — Investigation/Planning/Safety/Execution/Verification/Audit (Opus reasoning + deterministic checks)
- **Patterns:** 5 Tool · 6 Planning · 7 Multi-agent · 11 Exception · 12 HITL · 14 Inter-agent
- **Output:** ordered recovery plan → executed step-by-step with verification + audit

### 15. Adaptive Transaction Routing `[GPT]` · Risk H
- **Trigger:** service degradation / capacity condition (never per-trade LLM)
- **Sources:** region, ccy pair, counterparty, market availability, service health, cutoff, risk threshold, downstream capacity
- **MCP tools:** `getRuntimeConditions()`, `proposeRoutingPolicy()`, `validateRoutingPolicy()` (rules service); *(gated)* `applyRoutingConfig()`
- **Models:** Reasoning=Opus (analyze conditions) · Policy=deterministic validate · Execution · Memory
- **Patterns:** 5 Tool · 6 Planning · 12 HITL · 15 Resource-aware
- **Output:** temp routing policy → validated → approved → applied → observed

---

## Theme 6 — Observability → Business Reasoning

### 8. Runtime Change Correlation `[GPT]` · Risk L
- **Trigger:** business-behavior shift detected
- **Sources:** K8s deploys, GitOps events, config audit, rule-deploy history, flags, schema registry, Databricks job history, ELK, Grafana, KPI DB
- **MCP tools:** `getRecentChanges(window)`, `correlateChangeToOutcome()`, `getChangeGraph(entity)`
- **Models:** Perception=Haiku · Detection=Python · Reasoning=Opus (causal) · Memory
- **Patterns:** 5 Tool · 8 Memory · 13 RAG · 16 Reasoning
- **Output:** "rejection↑14:05 ← rule pkg 7.14 @14:01 → EUR/GBP +28% → book B17"

### 9. Business KPI Guard `[GPT]` · Risk L
- **Trigger:** continuous KPI monitoring; anomaly wakes the agent
- **Sources:** per-region captured/validated/enriched/risk/booked/settled rates, reject reasons, ccy-pair & book distribution, late-event volume, EOD status
- **MCP tools:** `getBusinessKpis(region)`, `getSeasonalBaseline(kpi)`, `getRejectBreakdown()`
- **Python sidecar:** time-series anomaly + seasonal baseline (business-calendar aware)
- **Models:** Detection=Python (LLM only after anomaly) · Reasoning=Opus · Memory
- **Patterns:** 5 Tool · 10 Goal · 16 Reasoning · 17 Eval
- **Output:** "APAC booking 41% below 5-day norm; risk requests not emitted for APAC-17"

### 26. Distributed-Trace Latency Explanation `[CLA]` · Risk L
- **Trigger:** trade breaches per-stage SLA / on-demand
- **Sources:** OTel/Jaeger traces, ELK, actuator, dependency map (Neo4j)
- **MCP tools:** `getTradeTrace(tradeId)`, `getSpanBreakdown()`, `getServiceBaseline(span)`, `correlateToDeploy()`
- **Python sidecar:** trace ingestion + baseline stats
- **Models:** Perception=Haiku (span tree→facts) · Detection=Python · Reasoning=Opus · Memory
- **Patterns:** 5 Tool · 8 Memory · 16 Reasoning
- **Output:** "Redis call slow → enrichment stalled → missed cutoff"

### 27. Synthetic Business-Probe (Canary Trade) `[CLA]` · Risk M
- **Trigger:** scheduled per-region (e.g. every 5 min)
- **Sources:** the real pipeline (synthetic-tagged) + per-stage state stores
- **MCP tools:** `injectSyntheticTrade(region,pair)`, `traceSyntheticProgress()`, `assertExpectedLifecycle()`; *(gated)* `openBusinessDegradation()`
- **Guardrail:** synthetic trades non-settling, clearly tagged, sandboxed
- **Models:** Detection=deterministic stage-complete · Reasoning=Opus · Planning · Memory
- **Patterns:** 5 Tool · 17 Eval · 18 Guardrails · 20 Exploration
- **Output:** proves business liveness ≠ infra liveness; pinpoints stuck stage

### 33. Runtime Intent-Inference `[COP]` · Risk L
- **Trigger:** burst of system activity / on-demand
- **Sources:** logs, metrics, events, deploy/scaling signals
- **MCP tools:** `getRecentActivity(window)`, `classifyIntent()`, `alignToBusinessGoal()`
- **Python sidecar:** behavioral clustering
- **Models:** Perception=Haiku · Detection=Python cluster · Reasoning=Opus (intent) · Memory
- **Patterns:** 5 Tool · 16 Reasoning · 20 Exploration
- **Output:** "this is EOD ramp / failover, not an incident" → suppress false alarm

---

## Theme 7 — Dependency & Blast Radius

### 14. Relationship & Contagion Analysis (Neo4j) `[GPT]` · Risk L
- **Trigger:** a problem occurs (service / feed / counterparty failure)
- **Sources:** Neo4j (trade→pair/counterparty/book→region/rule/feed/service→topic)
- **MCP tools (Cypher templates only):** `findTradeDependencies()`, `findAffectedBooks()`, `findDownstreamAggregations()`, `findSharedMarketDataDependencies()`, `calculateBusinessBlastRadius()`
- **Models:** Planning=Opus · Graph=deterministic Cypher · Reasoning=Opus · Memory
- **Patterns:** 5 Tool · 6 Planning · 16 Reasoning · 18 Guardrails (no free-form Cypher)
- **Output:** blast radius — which trades/books/regions/aggregations affected

### 32. Microservice Genome / Knowledge-Profile `[COP]` · Risk L
- **Trigger:** pre-change analysis / scheduled
- **Sources:** OpenAPI specs, runtime logs, Kafka topics, deployment manifests, call patterns
- **MCP tools:** `getServiceProfile(svc)`, `getDependencies(svc)`, `predictFragility(svc)`, `findConsumersOfChange()`
- **Python sidecar:** semantic diff + architecture-pattern classifier
- **Models:** Perception=Haiku · Detection=Python · Reasoning=Opus (fragility) · Memory=Neo4j graph
- **Patterns:** 5 Tool · 14 Inter-agent · 16 Reasoning · 20 Exploration
- **Output:** service knowledge graph + which service is fragile to the next change

---

## Theme 8 — Capacity & Economics

### 16. Operational Capacity & Backlog Planning `[GPT]` · Risk H
- **Trigger:** backlog-vs-deadline check
- **Sources:** backlog, processing rate, partitions, concurrency, per-ccy complexity, deadline, downstream capacity, historical curves, retry volume, Databricks availability, DB load
- **MCP tools:** `getBacklog(region)`, `getCompletionEstimate()`, `proposeScalingPlan()`; *(gated)* `applyScalingPlan()`
- **Python sidecar:** capacity/completion model
- **Models:** Detection=Python capacity model · Reasoning=Opus (alternatives) · Planning · Execution
- **Patterns:** 5 Tool · 6 Planning · 12 HITL · 15 Resource-aware · 19 Prioritization
- **Output:** "2.4M backlog, 47m est vs 31m deadline; +8 replicas → 27m"

### 31. Runtime FinOps / Cost-Anomaly `[CLA]``[GEM]` · Risk H
- **Trigger:** cost anomaly / post-deploy spike / FX-volume-low schedule
- **Sources:** AWS Cost Explorer, Azure Cost Mgmt, Databricks billing, K8s metrics, deploy history, regional FX volume
- **MCP tools:** `getCostByService()`, `correlateCostToDeploy()`, `getIdleCapacity()`, `proposeRightsizing()`; *(gated)* `applyScaleDown()`
- **Python sidecar:** cost time-series anomaly
- **Models:** Detection=Python · Reasoning=Opus (cost←cause) · Planning · Execution
- **Patterns:** 5 Tool · 10 Goal · 12 HITL · 15 Resource-aware
- **Output:** "cost spike ← rule 7.14 doubled risk-engine calls"; volume-aware scale-down proposal

---

## Theme 9 — Supervisor / Interface

### 11. Cross-Service Business Conversation (Supervisor) `[GPT]` · Risk L (inherits sub-agent risk)
- **Trigger:** user chat / API / event
- **Sources:** all sub-agents
- **MCP tools:** `routeToAgent(intent)`; aggregates sub-agent envelopes
- **Models:** Routing=Sonnet/Opus (intent classify + delegate) · Reasoning=Opus · Memory=session
- **Patterns:** 2 Routing · 7 Multi-agent · 8 Memory · 12 HITL · 14 Inter-agent
- **Output:** single conversational interface over the whole agent fleet

---

## Phase-0 foundations (build before any agent)

These are shared and appear in nearly every card; build once:
1. **MCP tool contract** — Spring AI MCP server, envelope↔schema mapping, `dataClassification`, `expiresAt`, idempotency keys.
2. **Model router** — Haiku 4.5 / Sonnet 5 / Opus 4.8 selection by task (Pattern 15).
3. **Memory stores** — Redis (session/short), Postgres (episodic cases + audit), vector DB (similar-incident recall) (Pattern 8).
4. **Human-in-the-loop gate** — n8n `Wait` node + approval routing (Slack/Teams) before any M/H tool (Pattern 12).
5. **Golden-set eval harness** — fixed known-failure scenarios to regression-test agents (Pattern 17). *Biggest commonly-skipped gap.*
6. **Guardrails** — sandbox (shadow pods), treat log/event payloads as untrusted input (prompt-injection surface), no raw DB/shell/Cypher (Pattern 18).

## Recommended build order

| Phase | Agents | Why |
|---|---|---|
| **1 — MVP (low risk, high value)** | 1 Lifecycle · 11 Supervisor · 27 Canary · 17 DLQ | One coherent product; proves the full pattern stack; all safe |
| **2 — Readiness & risk** | 5 EOD · 4 Risk Explain · 9 KPI Guard · 21 Market-data · 10 Freshness | The FX-authentic core; mostly read/block |
| **3 — Action & recovery (high risk)** | 7 Recovery · 2 State Divergence · 18 Duplicate-Effect · 34 Shadow Rule | Introduces gated execution + shadow simulation |
| **4 — Correlation & dependency** | 8 Change Correlation · 14 Contagion · 32 Genome · 33 Intent · 26 Trace · 6 Rule Impact | Advanced reasoning over the runtime graph |
| **5 — Capacity & economics** | 16 Backlog · 31 FinOps · 19 Lag Predictor · 28 Retry-storm | Scaling/cost — highest-autonomy, gate hardest |
| **6 — Completeness & edges** | 3 Event Integrity · 12 Materiality · 13 Exposure · 15 Routing · 20 Schema Drift · 22 Cutoff · 23 Ripple · 24 Settlement · 25 Rule Coverage · 29 Databricks Lineage · 30 Reg Reporting | Slot in by theme as the platform matures |

## Risk distribution
- **L (read/explain, 12):** 1, 4, 8, 9, 11, 13, 14, 25, 26, 32, 33 + 20(advisory)
- **M (blocks/quarantines/holds, ~13):** 2, 3, 5, 10, 12, 17, 21, 22, 23, 27, 29, 30
- **H (moves money/state/config — always gated, ~9):** 6, 7, 15, 16, 18, 19, 24, 28, 31, 34

## The 20 agentic design patterns (index)
1 Prompt Chaining · 2 Routing · 3 Parallelization · 4 Reflection · 5 Tool Use · 6 Planning · 7 Multi-Agent Collaboration · 8 Memory Management · 9 Learning & Adaptation · 10 Goal Setting & Monitoring · 11 Exception Handling & Recovery · 12 Human-in-the-Loop · 13 Retrieval (RAG) · 14 Inter-Agent Communication · 15 Resource-Aware Optimization · 16 Reasoning (CoT/ToT/debate/self-consistency) · 17 Evaluation & Monitoring · 18 Guardrails & Safety · 19 Prioritization · 20 Exploration & Discovery

> **Recurring lesson across all four sources:** every rival that tried to impress reached for *autonomy* (auto-freeze, auto-scale, auto-adjust thresholds). Every one is rewritten here as **propose → deterministic simulation → impact report → human approval → controlled MCP tool**. That gate is what makes these safe to run against a bank's FX runtime.
