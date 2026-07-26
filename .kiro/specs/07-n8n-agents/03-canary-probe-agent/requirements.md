# Requirements Document — Synthetic Business-Probe (Canary Trade) Agent

> **Technology-agnostic spec.** References **Technology Roles** from
> `01-initial-setup/01-technology-stack`. Contains no product names or versions.
> Inherits `architecture-golden-path/01-service-nfrs` for cross-cutting NFRs.

## Introduction

This feature defines the **Synthetic Business-Probe Agent** (Canary Trade
Agent) — a scheduled agent that continuously proves **business liveness** by
injecting synthetic trades into the real pipeline and tracing their progress
through each processing stage. Unlike infrastructure health checks (ping,
readiness probes), this agent validates that the end-to-end business pipeline
is actually processing trades correctly and within SLA.

The agent injects clearly tagged, non-settling synthetic trades per region on a
configurable schedule, traces each synthetic trade through the expected
lifecycle stages, asserts that the expected lifecycle is reached within the
stage SLA, and triggers a business degradation alert when a stage is stuck.

This agent is implemented as an `AGENT_PLATFORM` workflow export. It calls
typed `AGENT_TOOL_PROTOCOL` tools for injection and tracing. Synthetic trades
are sandboxed and never settle.

All identifiers in examples use the synthetic `FX-` prefix. All organization
names are fictional.

---

## Glossary

- **CanaryProbeAgent**: The `AGENT_PLATFORM` workflow that injects and traces
  synthetic trades to prove business liveness.
- **SyntheticTrade**: A trade injected by the `CanaryProbeAgent` that is
  clearly tagged (e.g. `FX-CANARY-*` prefix, `synthetic=true` flag) and is
  configured to never reach settlement.
- **BusinessLiveness**: The property that the trade processing pipeline is
  correctly advancing trades through lifecycle stages, as distinct from
  infrastructure liveness (services are up and responding to pings).
- **StageSLA**: The maximum expected duration for a trade to transition from
  one lifecycle stage to the next (e.g. CAPTURED to VALIDATED within 30s).
- **StuckStage**: A pipeline stage where the `SyntheticTrade` has not
  progressed within its `StageSLA`.
- **BusinessDegradation**: An operational alert opened when a `StuckStage` is
  detected, indicating the pipeline is functionally impaired despite
  infrastructure being healthy.
- **ProbeSchedule**: The configurable interval at which the `CanaryProbeAgent`
  injects synthetic trades per region (default: every 5 minutes).
- **DetectionModel**: Deterministic stage-completion assertions (no LLM).
- **ReasoningModel**: The LLM tier used for diagnosing which stage is stuck
  and why (deep reasoning).
- **PlanningModel**: The LLM tier used for determining remediation
  recommendations when degradation is detected.
- **SyntheticData**: Test/example data using only `FX-` prefixed identifiers
  and fictional names.

---

## Requirements

### Requirement 1: Synthetic Trade Injection

**User Story:** As a platform operator, I want synthetic trades injected into
each region's pipeline on a schedule, so that I can continuously validate
business liveness without relying on real trade flow.

#### Acceptance Criteria

1. THE `CanaryProbeAgent` SHALL call the `injectSyntheticTrade`
   `AGENT_TOOL_PROTOCOL` tool (on `trade-ingest-service`) to create a
   `SyntheticTrade` for a specified region and currency pair.
2. EVERY injected `SyntheticTrade` SHALL carry a `synthetic=true` metadata
   flag and a trade ID matching the pattern `FX-CANARY-{region}-{timestamp}`
   so it is unambiguously identifiable as non-real.
3. THE `CanaryProbeAgent` SHALL inject one `SyntheticTrade` per active region
   per `ProbeSchedule` interval (configurable, default every 5 minutes).
4. THE `CanaryProbeAgent` SHALL rotate currency pairs across injections to
   exercise different processing paths (e.g. EUR/USD, GBP/JPY, USD/SGD).
5. THE `injectSyntheticTrade` tool SHALL reject injection if the synthetic
   sandbox guardrail is not enabled on the target service, returning
   `status = FAILURE` with violation `SANDBOX_NOT_ENABLED`.

---

### Requirement 2: Synthetic Trade Sandbox Guardrail

**User Story:** As a risk stakeholder, I want absolute certainty that synthetic
trades never settle or affect real positions, so that the canary mechanism
cannot cause financial impact.

#### Acceptance Criteria

1. EVERY `SyntheticTrade` SHALL be tagged at creation with `synthetic=true`
   and SHALL be excluded from settlement processing by the downstream
   services (enforced by the `SERVICE_FRAMEWORK` business logic, not the agent).
2. THE `CanaryProbeAgent` SHALL verify after injection that the
   `SyntheticTrade` carries the `synthetic=true` flag by reading it back via
   the `getTrade` tool; if the flag is missing, THE agent SHALL immediately
   raise a critical alert and halt further injections.
3. `SyntheticTrade` records SHALL be automatically purged from the pipeline
   after a configurable retention period (default 24 hours) to prevent
   accumulation.
4. THE `CanaryProbeAgent` SHALL NOT inject synthetic trades if it detects that
   the sandbox guardrail has been disabled on any target service.
5. ALL synthetic trade identifiers SHALL match the `FX-CANARY-*` pattern and
   SHALL be excluded from all reporting, risk aggregation, and EOD processing
   by the deterministic services.

---

### Requirement 3: Stage Progress Tracing

**User Story:** As a platform operator, I want the agent to trace each
synthetic trade's progress through every pipeline stage, so that I can see
exactly where processing succeeds or stalls.

#### Acceptance Criteria

1. THE `CanaryProbeAgent` SHALL call the `traceSyntheticProgress`
   `AGENT_TOOL_PROTOCOL` tool to retrieve the current lifecycle stage and
   timestamp for each active `SyntheticTrade`.
2. THE `CanaryProbeAgent` SHALL compare each stage transition timestamp
   against the `StageSLA` for that transition.
3. WHEN a `SyntheticTrade` advances to the next expected stage within
   `StageSLA`, THE agent SHALL record the successful transition with its
   latency.
4. WHEN a `SyntheticTrade` has NOT advanced past a stage within `StageSLA`,
   THE agent SHALL classify that stage as a `StuckStage`.
5. THE `CanaryProbeAgent` SHALL track all active synthetic trades concurrently
   (one per region) and report per-region liveness status.

---

### Requirement 4: Business Degradation Detection and Alerting

**User Story:** As a platform operator, I want automatic alerting when a
pipeline stage is stuck, so that I learn about business-level failures before
they accumulate enough to cause a visible incident.

#### Acceptance Criteria

1. WHEN the `CanaryProbeAgent` detects a `StuckStage`, IT SHALL call the
   `assertExpectedLifecycle` `AGENT_TOOL_PROTOCOL` tool to confirm the
   expected vs. observed state.
2. WHEN the assertion confirms a stuck stage, THE `CanaryProbeAgent` SHALL
   call the `openBusinessDegradation` `AGENT_TOOL_PROTOCOL` tool (Risk M,
   gated) to create a business degradation alert specifying: the region, the
   stuck stage, the synthetic trade ID, the elapsed time, and the expected SLA.
3. THE `openBusinessDegradation` tool call SHALL require HITL approval before
   execution because it is classified as `ToolRisk` M (it triggers
   operational response workflows).
4. THE `CanaryProbeAgent` SHALL use the `ReasoningModel` to provide a
   diagnostic explanation of why the stage may be stuck, based on the
   available `ToolEnvelope` facts.
5. THE `CanaryProbeAgent` SHALL NOT open duplicate degradation alerts for the
   same region and stage if an alert is already active (idempotent alerting).

---

### Requirement 5: Model Tier Allocation

**User Story:** As a platform architect, I want the Canary Probe Agent to use
the appropriate tier for each cognitive task, optimizing cost for a
high-frequency scheduled agent.

#### Acceptance Criteria

1. THE `CanaryProbeAgent` SHALL use **deterministic assertions** (no LLM) for
   stage-completion detection — comparing timestamps against SLA thresholds
   is arithmetic, not reasoning.
2. THE `CanaryProbeAgent` SHALL use the **deep reasoning model**
   (`ReasoningModel`) only when a `StuckStage` is detected, to diagnose the
   probable cause and draft the degradation context.
3. THE `CanaryProbeAgent` SHALL use the **planning model** (`PlanningModel`)
   to recommend remediation steps when opening a business degradation alert.
4. THE `CanaryProbeAgent` SHALL maintain a rolling memory of probe results
   (per-region stage latencies over the last 24 hours) in the `CACHE` role
   for trend detection.
5. THE `CanaryProbeAgent` SHALL NOT use any LLM tier for the happy-path
   (all stages pass) — LLM invocation occurs only on degradation, keeping
   per-probe cost near zero in steady state.

---

### Requirement 6: Probe Schedule Configuration

**User Story:** As a platform operator, I want to configure probe frequency
and target regions without modifying the agent workflow, so that I can adapt
to operational conditions.

#### Acceptance Criteria

1. THE `ProbeSchedule` interval SHALL be externalized as configuration
   (default: 5 minutes per region).
2. THE list of active regions to probe SHALL be externalized as configuration
   and SHALL default to all regions defined in the `business-calendar-service`.
3. THE `StageSLA` thresholds per stage SHALL be externalized as configuration
   with sensible defaults (e.g. CAPTURED to VALIDATED: 30s, VALIDATED to
   ENRICHED: 60s, ENRICHED to RISK_CALCULATED: 120s).
4. THE currency pair rotation list SHALL be externalized as configuration.
5. CHANGES to probe configuration SHALL take effect on the next scheduled
   execution without restarting the `AGENT_PLATFORM` instance.

---

## Domain Acceptance Scenarios (Golden-Set Eval Harness)

| Scenario ID | Input | Expected Behavior |
|---|---|---|
| CAN-EVAL-01 | Scheduled probe fires for APAC region; all stages complete within SLA | Records successful liveness; no alert; reports per-stage latencies |
| CAN-EVAL-02 | Probe for EMEA: synthetic trade FX-CANARY-EMEA-1721900000 stuck at ENRICHED for 3x SLA | Detects StuckStage at ENRICHED; calls assertExpectedLifecycle; proposes openBusinessDegradation (HITL gated) |
| CAN-EVAL-03 | Probe injection returns SANDBOX_NOT_ENABLED | Agent halts; raises critical alert; does not inject |
| CAN-EVAL-04 | Read-back of injected trade missing synthetic=true flag | Agent raises critical alert; halts all further injections for that region |
| CAN-EVAL-05 | Duplicate degradation: EMEA ENRICHED already has active alert | Agent does NOT open duplicate alert; logs idempotent skip |
| CAN-EVAL-06 | All three regions probed concurrently: APAC pass, EMEA stuck, AMER pass | Reports per-region status; only EMEA triggers degradation flow |

---

## Agentic Design Patterns

| Pattern | Application in this Agent |
|---|---|
| 5 — Tool Use | Calls injectSyntheticTrade, traceSyntheticProgress, assertExpectedLifecycle, openBusinessDegradation |
| 17 — Evaluation | Continuous liveness evaluation via probe results |
| 18 — Guardrails | Synthetic sandbox enforcement; never-settle guarantee |
| 20 — Exploration | Rotates currency pairs and regions to explore different paths |
| 12 — Human-in-the-Loop | openBusinessDegradation requires HITL approval (Risk M) |

---

## Risk Classification

- **Risk level:** M (blocks/quarantines — opening a business degradation
  alert triggers operational response)
- **Side effects:** Injects synthetic trades (sandboxed, non-settling); may
  open business degradation alerts (gated)
- **HITL requirement:** Required before executing `openBusinessDegradation`

---

## MCP Tools Called

| Tool Name | Service | Risk | Purpose |
|---|---|---|---|
| `injectSyntheticTrade` | `trade-ingest-service` | L | Inject a tagged synthetic trade into the pipeline |
| `getTrade` | `trade-lifecycle-service` | L | Verify synthetic flag on injected trade |
| `traceSyntheticProgress` | `trade-lifecycle-service` | L | Retrieve current stage of a synthetic trade |
| `assertExpectedLifecycle` | `trade-lifecycle-service` | L | Confirm expected vs observed lifecycle state |
| `openBusinessDegradation` | `eod-processing-service` | M | Open a business degradation alert (gated) |

---

## Python Sidecar Dependency

None. The `CanaryProbeAgent` is triggered by a schedule (cron/interval timer
in the `AGENT_PLATFORM`), not by a Python sidecar. All detection logic is
deterministic (timestamp comparison against SLA thresholds).
