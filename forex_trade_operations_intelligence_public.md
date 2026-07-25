# Runtime Intelligence Agents for Forex Trade Operations

> **Public reference implementation notice**  
> This document contains generalized architecture patterns and fictional examples created for an open-source reference implementation. Trade identifiers, timestamps, rule versions, regions, branches, services, payloads, APIs, and operational scenarios are illustrative only. They do not represent any employer, client, financial institution, production system, or confidential implementation.


This reference architecture focuses on runtime intelligence rather than generic chatbots, PR-review agents, ticket creators, or “ask your database” demonstrations.

The central design objective is:

> **Agents that observe live enterprise applications, reconstruct business state across distributed services, reason about what is happening, and coordinate safe actions through existing Spring Boot and Python APIs.**

The agent should **not replace your microservices**. Your Java/Python services remain responsible for:

- Transaction processing
- Trade calculations
- Referential integrity
- Rules execution
- Event publishing
- Authentication and authorization
- Low-latency decisions
- Auditable state changes

n8n becomes the **agent orchestration and decision-coordination layer** surrounding those services.

n8n currently supports tool-using agents, multiple model providers, workflow-as-tool patterns, specialized subagents, several memory mechanisms, Kafka triggers, human approval before sensitive tool calls, and horizontally scalable execution through queue-mode workers.

---

# 1. Global Trade Lifecycle Reconstruction Agent

## Business problem

A foreign-exchange trade passes through numerous services:

```text
Trade captured
→ validated
→ enriched
→ priced
→ risk calculated
→ booked
→ allocated
→ confirmed
→ settled
```

When someone asks:

> “What exactly happened to trade FX-928734?”

the information may be spread across Kafka, Event Hub, PostgreSQL, MongoDB, Redis, ELK and multiple microservices.

## What the agent does

The agent dynamically reconstructs the trade’s complete business journey:

- Finds the original trade
- Retrieves all related events by trade ID and correlation ID
- Orders events by event time and ingestion time
- Detects missing, duplicated or out-of-order events
- Retrieves current trade state
- Compares current state with expected lifecycle state
- Explains where the trade stopped
- Identifies the responsible service or business rule
- Recommends the next safe action

## Sources

- Kafka trade topics
- Azure Event Hub
- PostgreSQL trade tables
- MongoDB trade documents
- Redis state/cache
- ELK/OpenSearch logs
- Spring Boot actuator endpoints
- Trade audit database
- Business-calendar service

## Tools exposed to the agent

```text
getTrade()
getTradeEvents()
getTradeAuditHistory()
getServiceProcessingStatus()
getExpectedLifecycle()
getBusinessCalendar()
replayTradeEvent()
requestTradeReprocessing()
```

These tools can be implemented as controlled Spring Boot or FastAPI endpoints.

## Agent specialization

| Component | Responsibility |
|---|---|
| Perception agent | Collects events, records and telemetry |
| Timeline agent | Reconstructs chronological sequence |
| Reasoning agent | Determines where actual and expected states diverged |
| Policy agent | Determines whether replay/reprocessing is permitted |
| Action agent | Calls approved recovery endpoints |
| Memory | Stores prior failures and successful resolutions |

## Model requirements

- **Small extraction model:** normalize logs and event payloads
- **Reasoning model:** lifecycle reconstruction and causal explanation
- **Embedding model:** retrieve similar historical trade failures
- **No model:** actual trade calculations or trade-state transitions

## Tangible output

```text
Trade: FX-928734
Current state: RISK_PENDING
Expected state: BOOKED

Observed:
14:03:01 TradeCaptured
14:03:02 TradeValidated
14:03:03 TradeEnriched
14:03:04 RiskCalculationRequested

Missing:
RiskCalculationCompleted

Probable cause:
A currency-pair rule could not resolve the applicable regional
business calendar for a fictional processing branch.

Recommended action:
Recalculate risk using the approved current calendar version.
```

This is far more valuable than searching logs manually.

---

# 2. Business-State Divergence Agent

## Business problem

A transaction can appear differently across systems:

- PostgreSQL says `BOOKED`
- MongoDB says `RISK_CALCULATED`
- Redis says `PENDING`
- Kafka contains `TRADE_CANCELLED`
- Reporting lake still shows `ACTIVE`

This is not merely a technical incident. It is a **business-state inconsistency**.

## What the agent does

It continuously or on demand:

1. Builds the canonical expected state.
2. Reads state from every participating system.
3. Detects divergence.
4. Determines which source is stale or incorrect.
5. Calculates the business impact.
6. Proposes an approved reconciliation workflow.

## Sources and tools

| Source | Tool |
|---|---|
| PostgreSQL | `queryTradeState()` |
| MongoDB | `getTradeDocument()` |
| Redis | `getCachedTradeState()` |
| Kafka | `getLatestDomainEvent()` |
| Databricks | `getAnalyticsTradeState()` |
| Rules service | `evaluateCanonicalState()` |

## Best architecture

The LLM should **not decide the authoritative state by itself**.

Use a deterministic Spring Boot `StateReconciliationService` that returns:

```json
{
  "states": {},
  "expectedState": "BOOKED",
  "violatedInvariants": [],
  "permittedActions": []
}
```

The agent interprets the result, investigates context and coordinates the resolution.

---

# 3. Event Integrity and Business Sequence Agent

## Business problem

Kafka monitoring usually tells you:

- Consumer lag
- Partition status
- Failed consumers
- Throughput

But it does not necessarily tell you:

> “Are business events arriving in a logically valid sequence?”

## What the agent detects

- `TradeBooked` before `TradeValidated`
- `RiskCalculated` without `TradeEnriched`
- Duplicate settlement instructions
- An event generated twice with different payloads
- Sequence-number gaps
- A child event with no parent event
- Events arriving after business-day closure
- A replay accidentally producing a second business transaction

## Agent design

### Perception layer

A Spring Boot Kafka Streams or Python stream-processing service maintains compact sequence facts:

```json
{
  "tradeId": "FX-928734",
  "observedEvents": [],
  "missingEvents": [],
  "duplicates": [],
  "sequenceViolations": []
}
```

### Reasoning layer

n8n invokes the agent only when:

- A violation occurs
- A material threshold is exceeded
- Someone requests an investigation

### Action layer

- Quarantine event
- Pause downstream processing for one trade
- Request replay
- Create reconciliation case
- Notify operations
- Route to a human approver

n8n has Kafka trigger and Kafka send-message integrations, but high-volume business events should not be sent directly through an LLM workflow. Use stream processors for continuous detection, then trigger n8n with a compact anomaly envelope.

---

# 4. Trade Risk Explainability Agent

## Business problem

Risk engines produce numbers, but business users frequently ask:

- Why did this trade’s risk increase?
- Which currency-pair rule was applied?
- Which market factor changed?
- Why does regional risk differ from global risk?
- Why was the trade accepted yesterday but rejected today?

## What the agent does

The agent gathers:

- Trade characteristics
- Currency pair
- Applicable Drools rule
- Rule version
- Market data snapshot
- Geographic region
- Trading book
- Counterparty classification
- Prior risk result
- Current risk result
- Business calendar
- Limit configuration

It then produces a traceable explanation.

## Important boundary

The model must not calculate official risk.

Your deterministic risk service should return:

```json
{
  "riskResult": 1845000,
  "previousRiskResult": 1210000,
  "contributingFactors": [
    {
      "factor": "USD-INR volatility",
      "contribution": 410000
    }
  ],
  "rulesFired": [
    "FX-REGION-APAC-042"
  ]
}
```

The agent explains, compares and answers follow-up questions.

## Specialized models

| Model class | Use |
|---|---|
| Reasoning model | Multi-factor explanation |
| Small language model | Convert rule traces into readable descriptions |
| Embedding model | Retrieve rule documentation and prior cases |
| Time-series/statistical model | Detect meaningful market/risk deviations |
| Deterministic engine | Official risk computation |

---

# 5. End-of-Day Risk Readiness Agent

This is especially suitable for globally distributed foreign-exchange processing.

## Business problem

Global end-of-day processing is not one simple batch. It depends on:

- Regional market closure
- Local calendars
- Branch completion
- Missing trades
- Late events
- Data reconciliation
- Exchange rates
- Risk aggregation
- Trading-book closure
- Reruns
- US-based global-day completion

## What the agent does

The agent continuously maintains a **global closure readiness map**.

```text
APAC: Ready
EMEA: Blocked
Americas: In progress
Global consolidation: Not ready
```

It answers:

- Which regions have closed?
- Which branches are incomplete?
- Which missing datasets block the global calculation?
- Are late trades material?
- Can aggregation proceed with an exception?
- What is the predicted completion time?
- Which reruns are necessary?
- What requires operational approval?

## Agent hierarchy

### Regional readiness agents

One specialized agent per region:

- APAC agent
- EMEA agent
- Americas agent

### Global supervisor agent

Combines regional status and evaluates global readiness.

## Tools

```text
getRegionalCloseStatus(region)
getUnprocessedTradeCount(region)
getLateTradeMateriality(region)
getMarketDataReadiness(region)
getBranchCompletionStatus(region)
getRiskAggregationStatus(region)
startRegionalRerun(region)
approveException(region)
startGlobalConsolidation()
```

## Business value

This becomes an intelligent **global command center**, not another monitoring dashboard.

---

# 6. Runtime Business Rule Impact Agent

## Business problem

A Drools rule can be syntactically valid and still cause unexpected production behavior.

Examples:

- A currency-pair rule rejects too many trades
- A newly deployed threshold changes regional risk
- Two rules overlap
- A rule is never reached
- A fallback rule fires excessively
- The same business condition produces different outcomes across regions

## What the agent does

- Observes rule execution telemetry
- Detects changes in firing patterns
- Compares pre-deployment and post-deployment behavior
- Identifies affected trades, books and regions
- Finds overlapping or conflicting rules
- Generates a business-impact explanation
- Recommends rollback, disablement or investigation

## Sources

- Drools audit events
- Rule repository
- Rule-version metadata
- Trade decision outcomes
- Kafka events
- Databricks historical analysis
- Feature/configuration service
- Deployment history

## Models

- **Statistical anomaly detector:** firing-rate changes
- **Reasoning model:** relationship between rule changes and business outcomes
- **Embedding model:** similar rules and past defects
- **Deterministic rule service:** simulation and validation

## Safe action

The agent should never edit or activate Drools rules directly.

Instead:

```text
Agent proposes change
→ deterministic simulation
→ impact report
→ human approval
→ controlled rules-service deployment
```

---

# 7. Transaction Recovery Coordinator Agent

## Business problem

Recovery frequently requires coordinating several actions:

1. Confirm the original transaction state.
2. Verify idempotency.
3. Clear or retain cache.
4. Replay an event.
5. Reinvoke a downstream service.
6. Validate the resulting state.
7. Reconcile reporting.
8. Create an audit record.

Operators often execute these steps manually.

## What the agent does

The agent generates a transaction-specific recovery plan:

```text
Step 1: Verify that no settlement instruction exists.
Step 2: Confirm replay key has not been consumed.
Step 3: Invalidate stale risk cache entry.
Step 4: Replay RiskCalculationRequested.
Step 5: Wait for RiskCalculationCompleted.
Step 6: Compare PostgreSQL and MongoDB state.
Step 7: Close recovery case.
```

## Agent roles

| Agent | Role |
|---|---|
| Investigation agent | Determines why processing stopped |
| Planning agent | Produces ordered recovery plan |
| Safety agent | Checks idempotency and action permissions |
| Execution agent | Calls recovery APIs |
| Verification agent | Confirms successful outcome |
| Audit agent | Records evidence and decisions |

## Critical design principle

Each recovery action should be a narrow application API:

```text
POST /trades/{id}/risk-recalculation-requests
POST /events/{id}/replay-requests
POST /trades/{id}/cache-invalidation-requests
```

Do not give the agent:

- Generic database write access
- Arbitrary shell access
- Unrestricted Kafka production
- Administrative Kubernetes credentials

---

# 8. Runtime Change Correlation Agent

## Business problem

A business behavior change may correlate with:

- New application version
- Configuration change
- Rule deployment
- Schema change
- Infrastructure scaling
- New market-data feed
- Feature flag
- Redis eviction
- Kafka partition reassignment
- Databricks job change

Traditional monitoring shows these separately.

## What the agent does

It constructs a **runtime change graph**:

```text
Trade rejection increased at 14:05
    ↓
Rule package 7.14 activated at 14:01
    ↓
New rule affected EUR crosses
    ↓
EUR/GBP rejection increased 28%
    ↓
EMEA trading book B17 primarily affected
```

## Sources

- Kubernetes deployments
- GitOps deployment events
- Configuration audit
- Rule deployment history
- Feature flags
- Kafka schema registry
- Databricks job history
- ELK logs
- Grafana metrics
- Business KPI database

## Differentiator

This agent correlates **technical changes with business outcomes**, not merely technical symptoms.

---

# 9. Business KPI Guard Agent

## Business problem

Infrastructure may be completely healthy while the application is failing functionally.

Examples:

- All pods are running, but no trades are being booked
- Event throughput is normal, but risk-completion rate has fallen
- API latency is normal, but rejection rate has doubled
- Consumers are active, but one currency pair has disappeared
- Global volume is normal, but one region has stopped processing

## What the agent monitors

- Trades captured per region
- Validation success rate
- Enrichment completion rate
- Risk-completion rate
- Booking rate
- Settlement completion
- Reject reasons
- Currency-pair distribution
- Trading-book distribution
- Late-event volume
- EOD completion status

## Specialized intelligence

Use a combination of:

- Time-series anomaly detection
- Seasonal baselines
- Business-calendar awareness
- Event-volume comparison
- LLM reasoning only after an anomaly has been detected

## Output

```text
Technical health: Normal

Business anomaly:
APAC trade booking rate is 41% below the equivalent period
for the previous five business days.

Probable explanation:
JPY market-data enrichment is completing, but the subsequent
risk requests are not being emitted for rule group APAC-17.

Affected:
1,842 trades
3 trading books
Estimated notional exposure: supplied by Risk Exposure API
```

---

# 10. Data Freshness and Decision-Suitability Agent

## Business problem

Data being present does not mean it is suitable for a business decision.

A risk calculation may use:

- Stale foreign-exchange rates
- Incomplete counterparty data
- Old limits
- Incorrect geographic mapping
- Delayed branch data
- Unfinished Databricks aggregation

## What the agent does

Before an important process runs, it answers:

> “Is every required dataset sufficiently complete, timely and authoritative for this decision?”

## Sources

- Data catalog
- Databricks tables
- Market-data service
- Data-quality results
- Schema registry
- Lineage metadata
- Source-system timestamps
- Business criticality policies

## Agent output

```text
Dataset: FX_MARKET_RATE
Freshness: 7 minutes
Permitted maximum: 5 minutes
Impact: EMEA end-of-day risk calculation
Decision: BLOCK

Dataset: COUNTERPARTY_LIMIT
Freshness: 19 minutes
Permitted maximum: 60 minutes
Decision: ACCEPT
```

The policy determination should be deterministic; the agent provides investigation, context and coordination.

---

# 11. Cross-Service Business Conversation Agent

This is not a generic chatbot. It is an interface to the **running business application**.

## Example questions

- “Which EMEA trades are preventing end-of-day closure?”
- “Why did the USD/INR risk total increase since the previous calculation?”
- “Show trades that were captured but never booked.”
- “Which rule versions contributed to today’s rejected trades?”
- “Which branches have incomplete risk aggregation?”
- “What changed before APAC booking volume fell?”
- “Can trade 91827 be safely replayed?”
- “Which Redis entries disagree with PostgreSQL?”
- “What business processes are currently degraded?”

## Architecture

```text
User
  ↓
n8n Supervisor Agent
  ├── Trade Lifecycle Agent
  ├── Risk Explanation Agent
  ├── Rule Analysis Agent
  ├── Event Integrity Agent
  ├── Data Readiness Agent
  └── Recovery Planning Agent
           ↓
Controlled Spring Boot/FastAPI tools
           ↓
Enterprise systems
```

This can become the primary interface for production support, trading operations, risk analysts and business controllers.

---

# 12. Exception Materiality Agent

## Business problem

Not every failure should block a global process.

Suppose:

- 12 trades are delayed
- 4 have insignificant notional value
- 2 belong to a closed trading book
- 3 are duplicates
- 3 are high-risk and material

The decision is not simply “error exists.”

## What the agent does

- Groups unresolved exceptions
- Retrieves exposure and notional value
- Applies materiality policies
- Identifies regulatory or counterparty implications
- Separates tolerable exceptions from blockers
- Produces an approval package

## Output

```text
Total unresolved trades: 12

Non-material: 7
Operational follow-up: 2
Global-close blockers: 3

Recommendation:
Proceed with regional aggregation.
Do not begin global consolidation until the three blocker
trades are resolved or an authorized exception is approved.
```

The materiality calculation belongs in a rules or risk service. The model explains and coordinates.

---

# 13. Counterparty Exposure Narrative Agent

## Business problem

Senior risk managers do not want raw records from 12 systems.

They want:

- What changed?
- Why did it change?
- Is it material?
- What is concentrated?
- Which trades contributed?
- What action is required?

## Agent behavior

The agent combines:

- Counterparty trades
- Current exposure
- Limits
- Geographic exposure
- Currency-pair concentration
- Trading-book concentration
- Collateral
- Previous-day exposure
- Exceptions and delayed trades

It creates a live, traceable exposure narrative.

## Model composition

- Analytical SQL/aggregation service
- Graph traversal service over Neo4j
- Reasoning model
- Retrieval model for policies
- Small summarization model
- No direct model-generated arithmetic

---

# 14. Relationship and Contagion Analysis Agent Using Neo4j

## Business problem

A failed service, counterparty or market-data provider can have indirect impact.

Neo4j may contain relationships such as:

```text
Trade → Currency Pair
Trade → Counterparty
Trade → Trading Book
Trading Book → Region
Trade → Rule
Trade → Market Data Feed
Trade → Processing Service
Service → Kafka Topic
```

## What the agent does

When a problem occurs, it traverses the graph to answer:

- Which trades can be affected?
- Which books are exposed?
- Which regions depend on this feed?
- Which rules use this reference data?
- Which downstream aggregations are contaminated?
- What is the blast radius?

## Agent/tool split

The agent creates the investigation plan.

A deterministic graph service executes approved Cypher query templates:

```text
findTradeDependencies()
findAffectedBooks()
findDownstreamAggregations()
findSharedMarketDataDependencies()
calculateBusinessBlastRadius()
```

Avoid letting the model produce unrestricted production Cypher.

---

# 15. Adaptive Transaction Routing Agent

## Business problem

A transaction may need to be routed based on:

- Region
- Currency pair
- Counterparty
- Market availability
- Service degradation
- Regulatory restriction
- Processing cutoff
- Risk threshold
- Current downstream capacity

## What the agent does

The agent can recommend or coordinate routing:

```text
Normal route:
Trade Processor A → Risk Engine A

Current condition:
Risk Engine A degraded for exotic currency pairs

Permitted alternative:
Route eligible pairs to Risk Engine B
Keep restricted pairs in pending state
```

## Important limitation

Do not use an LLM synchronously for every trade.

Instead:

1. The agent analyzes current runtime conditions.
2. It proposes a temporary routing policy.
3. A deterministic rules service validates it.
4. An authorized person approves it.
5. Spring Boot applies the routing configuration.
6. The agent observes the result.

---

# 16. Operational Capacity and Backlog Planning Agent

## Business problem

Kafka lag alone does not tell you whether the system will finish before a business deadline.

## What the agent considers

- Current backlog
- Processing rate
- Partition count
- Consumer concurrency
- Per-currency complexity
- Regional deadline
- Downstream capacity
- Historical completion curves
- Retry volume
- Databricks availability
- Database load

## Output

```text
EMEA risk backlog: 2.4 million events
Current completion estimate: 47 minutes
Business deadline: 31 minutes

Recommended plan:
1. Increase risk-consumer replicas from 18 to 26.
2. Delay noncritical reconciliation workload.
3. Reserve database connection capacity.
4. Re-evaluate after 8 minutes.

Estimated completion after change: 27 minutes.
```

The scaling calculation should come from a capacity model, while the agent reasons over alternatives and coordinates approval.

---

# The Model Architecture You Should Use

You do not need one “super model.” Use a **model portfolio**.

| Intelligence type | Purpose | Suitable implementation |
|---|---|---|
| Perception | Read and normalize logs, events, documents | Small language model, parsers, classifiers |
| Statistical detection | Identify abnormal runtime behavior | Time-series model, rules, SQL, Spark |
| Reasoning | Explain multi-system business conditions | Strong reasoning LLM |
| Planning | Build investigation/recovery sequence | Reasoning LLM with tool descriptions |
| Execution | Perform approved actions | n8n tools calling Spring Boot/FastAPI |
| Policy | Decide what actions are permitted | Drools, policy service, deterministic code |
| Memory | Recall similar cases and previous outcomes | PostgreSQL, Redis, vector DB |
| Graph reasoning | Determine dependencies and blast radius | Neo4j plus controlled graph APIs |
| Calculation | Risk, exposure, totals and materiality | Java/Python deterministic services |
| Verification | Confirm action achieved intended state | Independent API queries and assertions |

---

# Where n8n Fits—and Where It Does Not

## Use n8n for

- Agent orchestration
- Multi-step investigation
- Tool selection
- Human approval
- Cross-system coordination
- Long-running workflows
- Notifications and escalation
- Case management
- Agent delegation
- Dynamic recovery planning
- Evidence collection
- Business explanations
- Calling Spring Boot and Python APIs

## Keep Spring Boot/Python for

- High-volume Kafka consumption
- Transactional consistency
- Trade processing
- Risk calculation
- Exact arithmetic
- Idempotency
- Authentication and authorization
- Rules evaluation
- Database transactions
- Low-latency routing
- Bulk event processing
- Schema validation
- Regulatory record generation

## Keep Databricks for

- Historical analytics
- Large-scale aggregations
- Pattern analysis
- Model training
- Trend and exposure calculations
- Backtesting
- Rule-impact analysis

## Keep Redis for

- Runtime state
- Short-lived agent context
- Idempotency keys
- Correlation information
- Cached business facts
- Workflow locks

## Keep Neo4j for

- Runtime dependency graphs
- Business entity relationships
- Blast-radius analysis
- Counterparty relationships
- Service-to-business-process mapping

---

# Recommended Reference Architecture

```text
                    ┌─────────────────────────┐
                    │ Operations / Risk Users │
                    └────────────┬────────────┘
                                 │
                         Chat / API / Event
                                 │
                    ┌────────────▼────────────┐
                    │ n8n Supervisor Agent    │
                    │                         │
                    │ Intent classification   │
                    │ Agent delegation        │
                    │ Plan coordination       │
                    │ Human approval          │
                    └────────────┬────────────┘
                                 │
       ┌─────────────────────────┼─────────────────────────┐
       │                         │                         │
┌──────▼────────┐      ┌─────────▼────────┐      ┌────────▼─────────┐
│ Lifecycle     │      │ Risk and Rules   │      │ Recovery Agent   │
│ Agent         │      │ Agent            │      │                  │
└──────┬────────┘      └─────────┬────────┘      └────────┬─────────┘
       │                         │                         │
       └─────────────────────────┼─────────────────────────┘
                                 │
                    Approved application tools
                                 │
       ┌─────────────────────────┼───────────────────────────┐
       │                         │                           │
┌──────▼──────────┐    ┌─────────▼─────────┐      ┌──────────▼───────┐
│ Spring Boot APIs│    │ Python/FastAPI    │      │ Query Services   │
│                 │    │ analytical tools │      │ and Graph APIs   │
└──────┬──────────┘    └─────────┬─────────┘      └──────────┬───────┘
       │                         │                           │
       └─────────────────────────┼───────────────────────────┘
                                 │
    Kafka / Event Hub / PostgreSQL / MongoDB / Redis / Neo4j /
                  ELK / Grafana / Databricks / Drools
```

---

# A Standard Agent Contract for Enterprise Microservices

Each tool endpoint should return an agent-friendly envelope:

```json
{
  "requestId": "req-12873",
  "businessEntity": {
    "type": "FX_TRADE",
    "id": "FX-928734"
  },
  "status": "SUCCESS",
  "facts": {},
  "violations": [],
  "permittedActions": [],
  "evidence": [
    {
      "source": "trade-risk-service",
      "timestamp": "2026-07-24T18:43:02Z",
      "reference": "risk-audit-28731"
    }
  ],
  "dataClassification": "CONFIDENTIAL",
  "expiresAt": "2026-07-24T18:48:02Z"
}
```

For action tools:

```json
{
  "action": "RECALCULATE_RISK",
  "entityId": "FX-928734",
  "reasonCode": "STALE_MARKET_DATA",
  "expectedVersion": 17,
  "idempotencyKey": "risk-recalc-FX-928734-v17",
  "approvalReference": "approval-8921",
  "dryRun": true
}
```

This is much safer than allowing natural-language-generated arbitrary payloads.

---

# Five Strong Product Directions

## 1. **Global Trade Command Agent**

Combines trade lifecycle, EOD readiness, regional status, risk aggregation and recovery coordination.

**Users:** trade operations, risk operations, application support.

## 2. **Business-State Reconciliation Agent**

Detects and resolves state divergence across events, caches, transaction stores and analytical stores.

**Users:** operations, engineering, audit.

## 3. **Risk Decision Explanation Agent**

Explains risk changes using rules, trade data, market data, limits and regional context.

**Users:** risk analysts, business leaders, compliance.

## 4. **Event Integrity and Recovery Agent**

Understands business-event sequencing, identifies missing transitions and safely coordinates replay.

**Users:** production support, platform engineering, transaction operations.

## 5. **Runtime Business Impact Agent**

Connects application changes, rules, infrastructure and data changes to real business outcomes.

**Users:** engineering leaders, product owners, operations executives.

---

# Recommended Starting Product

A practical starting point is a combined product:

## **Enterprise Transaction Intelligence Agent**

Its first three capabilities should be:

### Capability 1: Ask about any transaction

```text
“What happened to trade X?”
```

### Capability 2: Explain incomplete business processing

```text
“Why is this trade not included in EOD risk?”
```

### Capability 3: Produce a safe recovery plan

```text
“Can this trade be replayed, and what will happen if we do?”
```

Build the initial system with:

- **Spring Boot:** transaction, lifecycle, rules and recovery tools
- **FastAPI:** analytical and event-correlation tools
- **n8n:** supervisor, specialized agents, approvals and orchestration
- **PostgreSQL:** agent cases, evidence, plans and action audit
- **Redis:** temporary context, locks and idempotency
- **Neo4j:** transaction and application dependency graph
- **Kafka/Event Hub:** anomaly and business-event triggers
- **Databricks:** historical comparison and population-level impact
- **Drools:** permitted-action and materiality policy
- **Reasoning LLM:** investigation, explanation and planning
- **Small model:** classification and extraction
- **Embedding model:** retrieval of similar prior cases

The core differentiator would be:

> **It does not merely observe whether services are healthy. It understands whether the business transaction is healthy, why it reached its current state, what business impact exists, and which controlled action can safely restore it.**


---

# Public Repository Safeguards

This reference implementation should use only synthetic data and fictional organizations. Do not commit:

- Real trade, counterparty, account, customer, employee, or branch identifiers
- Employer or client names unless their use is explicitly authorized
- Production URLs, credentials, secrets, certificates, tokens, or internal hostnames
- Proprietary Kafka topics, database schemas, rule definitions, thresholds, or market-data contracts
- Screenshots, logs, payloads, architecture diagrams, or operational procedures copied from a workplace
- Confidential performance figures, transaction volumes, exposures, limits, incidents, or recovery actions

All examples should remain generic, reproducible, and independent of any real financial institution.
