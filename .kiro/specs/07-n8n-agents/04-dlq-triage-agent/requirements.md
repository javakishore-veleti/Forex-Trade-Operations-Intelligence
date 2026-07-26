# Requirements Document — DLQ Triage & Remediation Agent

> **Technology-agnostic spec.** References **Technology Roles** from
> `01-initial-setup/01-technology-stack`. Contains no product names or versions.
> Inherits `architecture-golden-path/01-service-nfrs` for cross-cutting NFRs.

## Introduction

This feature defines the **DLQ Triage & Remediation Agent** — a specialized
agent triggered when messages land on a dead-letter queue (DLQ) topic. Rather
than requiring operators to manually inspect each failed message, this agent
receives pre-clustered failure signatures from the `dlq-cluster-analyzer`
Python sidecar, determines whether each failure cluster is transient
(auto-replay candidate) or poison (quarantine), and proposes the appropriate
remediation action — always gated behind human approval before execution.

This agent is implemented as an `AGENT_PLATFORM` workflow export. It calls
typed `AGENT_TOOL_PROTOCOL` tools on the `state-reconciliation-service` when
divergence context is needed, and uses the pre-computed cluster output from
the Python sidecar as its primary input. It never auto-replays without
explicit human approval.

All identifiers in examples use the synthetic `FX-` prefix. All organization
names are fictional.

---

## Glossary

- **DLQTriageAgent**: The `AGENT_PLATFORM` workflow that triages dead-letter
  queue messages and proposes remediation.
- **DLQMessage**: A message that has exhausted retry attempts and been moved
  to a dead-letter topic in the `EVENT_STREAM` platform.
- **FailureSignature**: A clustered grouping of `DLQMessage` entries sharing
  the same root failure pattern (exception type, error code, affected service),
  as computed by the `dlq-cluster-analyzer` sidecar.
- **TransientFailure**: A `FailureSignature` whose root cause is temporary
  (e.g. downstream timeout, transient network partition) and where replay is
  expected to succeed.
- **PoisonMessage**: A `FailureSignature` whose root cause is structural
  (e.g. schema mismatch, invalid payload, business rule violation) and where
  replay would fail again.
- **ReplayProposal**: The agent's recommendation to replay a set of
  `DLQMessage` entries from a `TransientFailure` cluster, requiring HITL
  approval before execution.
- **Quarantine**: The action of moving `PoisonMessage` entries to a
  quarantine store for manual human review, removing them from the DLQ retry
  path.
- **HITL_Gate**: A human-in-the-loop approval checkpoint required before the
  agent replays any message.
- **PerceptionModel**: The LLM tier used for extracting structured failure
  information from raw DLQ message headers and error payloads (lightweight).
- **ReasoningModel**: The LLM tier used for determining transient vs poison
  classification and drafting remediation proposals (deep reasoning).
- **ClusterDetection**: The Python sidecar component that performs stack-trace
  clustering and embedding-based signature grouping (no LLM).
- **SyntheticData**: Test/example data using only `FX-` prefixed identifiers
  and fictional names.

---

## Requirements

### Requirement 1: Sidecar-Triggered Activation

**User Story:** As a platform operator, I want the DLQ Triage Agent to
activate automatically when the Python sidecar detects clustered failures, so
that DLQ messages are triaged promptly without manual monitoring.

#### Acceptance Criteria

1. THE `DLQTriageAgent` SHALL be triggered by a webhook or event from the
   `dlq-cluster-analyzer` Python sidecar (from
   `06-local-deploy/02-python-sidecars`) when one or more `FailureSignature`
   clusters are ready for triage.
2. THE trigger payload from the sidecar SHALL include: cluster ID, cluster
   size (number of messages), representative error signature (exception class,
   error code, stack trace hash), affected topic, affected service, earliest
   and latest message timestamps, and sample message keys.
3. THE `DLQTriageAgent` SHALL NOT process raw DLQ topic messages directly —
   it operates exclusively on pre-clustered `FailureSignature` envelopes
   produced by the Python sidecar.
4. WHEN the trigger payload is malformed or missing required fields, THE
   agent SHALL log a structured error and NOT proceed with triage.
5. THE `DLQTriageAgent` SHALL support batch processing: a single trigger may
   contain multiple `FailureSignature` clusters to triage in one execution.

---

### Requirement 2: Transient vs Poison Classification

**User Story:** As a platform operator, I want the agent to automatically
classify each failure cluster as transient or poison, so that I know which
messages are safe to replay and which require manual investigation.

#### Acceptance Criteria

1. THE `DLQTriageAgent` SHALL use the `ReasoningModel` (deep reasoning) to
   classify each `FailureSignature` as `TransientFailure` or `PoisonMessage`
   based on:
   - Error type (timeout, connection refused, 503 = likely transient; schema
     validation, deserialization, business rule = likely poison).
   - Whether the affected downstream service has since recovered (checked via
     health/readiness if available).
   - Whether similar signatures were successfully replayed in prior incidents
     (episodic memory).
   - Message age (very old messages may be stale regardless of cause).
2. THE `DLQTriageAgent` SHALL assign a confidence score to each
   classification (`high`, `medium`, `low`).
3. WHEN confidence is `low`, THE agent SHALL classify as `PoisonMessage`
   (safe default — quarantine rather than replay uncertain messages).
4. THE classification logic SHALL NOT use an LLM for the deterministic signals
   (error code matching, service health check) — the `PerceptionModel` is
   used only for extracting structured data from unstructured error payloads;
   the `ReasoningModel` synthesizes the final classification.

---

### Requirement 3: Replay Proposal (Transient Failures)

**User Story:** As a platform operator, I want the agent to propose replay for
transient failure clusters with a clear impact summary, so that I can approve
or deny the action with full context.

#### Acceptance Criteria

1. FOR EACH `TransientFailure` cluster, THE `DLQTriageAgent` SHALL generate a
   `ReplayProposal` containing:
   - Number of messages to replay.
   - Affected topic and partition(s).
   - Affected trade IDs (sample, max 10).
   - Root cause summary (why transient).
   - Current health of the downstream service.
   - Estimated impact of replay (e.g. "42 trades will resume enrichment").
2. THE `ReplayProposal` SHALL be presented to the human operator via the
   `HITL_Gate` mechanism; the agent SHALL halt and wait for explicit approval.
3. WHEN approval is granted, THE `DLQTriageAgent` SHALL call the
   `replayDlqMessage` `AGENT_TOOL_PROTOCOL` tool (Risk M, gated) with the
   `approvalReference` for each message in the cluster (or batch-replay if
   supported).
4. WHEN approval is denied, THE `DLQTriageAgent` SHALL log the denial, retain
   the cluster in a "reviewed-not-replayed" state, and take no further action.
5. THE `DLQTriageAgent` SHALL NOT auto-replay any message regardless of
   confidence level — HITL approval is always required.

---

### Requirement 4: Quarantine (Poison Messages)

**User Story:** As a platform operator, I want poison messages quarantined
automatically with a clear explanation, so that they are removed from the
retry path and I can investigate them separately.

#### Acceptance Criteria

1. FOR EACH `PoisonMessage` cluster, THE `DLQTriageAgent` SHALL call the
   `quarantineMessage` `AGENT_TOOL_PROTOCOL` tool (Risk M, gated) to move
   the messages to quarantine storage.
2. THE quarantine action SHALL require HITL approval before execution.
3. FOR EACH quarantined cluster, THE agent SHALL provide:
   - Root cause explanation (why classified as poison).
   - Sample message content (sanitized, synthetic IDs only).
   - Recommended manual investigation steps.
   - Which team/service owns the fix.
4. WHEN quarantine is approved and executed, THE `DLQTriageAgent` SHALL log
   the quarantine action with: cluster ID, message count, reason, approval
   reference, and timestamp.
5. THE `DLQTriageAgent` SHALL escalate the quarantined cluster for manual
   review via the appropriate notification channel (configured externally).

---

### Requirement 5: Divergence Context Retrieval

**User Story:** As a platform operator, I want the agent to check whether DLQ
failures caused state divergence, so that I understand the broader impact
before deciding on remediation.

#### Acceptance Criteria

1. WHEN the `FailureSignature` involves a trade state transition failure, THE
   `DLQTriageAgent` SHALL call the `evaluateCanonicalState`
   `AGENT_TOOL_PROTOCOL` tool (on `state-reconciliation-service`, Risk L) to
   determine if the failed message caused cross-system state divergence.
2. THE divergence context SHALL be included in both `ReplayProposal` and
   quarantine explanations so the operator sees the full impact.
3. WHEN divergence is detected, THE `DLQTriageAgent` SHALL elevate the
   priority of the cluster and recommend reconciliation as a follow-up action
   (delegation to the State Divergence Agent via Supervisor).
4. THE `DLQTriageAgent` SHALL NOT call `startReconciliation` (Risk M) itself
   — reconciliation is a separate agent's responsibility.

---

### Requirement 6: Model Tier Allocation

**User Story:** As a platform architect, I want the DLQ Triage Agent to use
the appropriate tier for each cognitive task, leveraging the Python sidecar
for detection and reserving LLM for reasoning.

#### Acceptance Criteria

1. THE `DLQTriageAgent` SHALL rely on the `dlq-cluster-analyzer` Python
   sidecar (`ClusterDetection`) for all failure clustering and signature
   grouping — no LLM is used for clustering.
2. THE `DLQTriageAgent` SHALL use the **lightweight perception model**
   (`PerceptionModel`) for extracting structured failure data from raw
   DLQ message headers and unstructured error strings.
3. THE `DLQTriageAgent` SHALL use the **deep reasoning model**
   (`ReasoningModel`) for transient-vs-poison classification, impact
   assessment, and remediation proposal drafting.
4. THE `DLQTriageAgent` SHALL maintain episodic memory of prior triage
   decisions (cluster signature, classification, outcome) in the
   `RELATIONAL_STORE` role for learning and similar-incident recall.
5. THE `DLQTriageAgent` SHALL NOT use any LLM tier for computing canonical
   state or permitted actions — these come from deterministic
   `SERVICE_FRAMEWORK` services (inherited GP-Rq-13).

---

## Domain Acceptance Scenarios (Golden-Set Eval Harness)

| Scenario ID | Input | Expected Behavior |
|---|---|---|
| DLQ-EVAL-01 | Sidecar triggers with 1 cluster: 42 messages, ConnectTimeoutException to enrichment-service (since recovered) | Classifies as transient; generates ReplayProposal for 42 messages; presents HITL gate |
| DLQ-EVAL-02 | Sidecar triggers with 1 cluster: 5 messages, SchemaValidationException (field "notional" type mismatch) | Classifies as poison; proposes quarantine with root cause "schema mismatch on notional field"; presents HITL gate |
| DLQ-EVAL-03 | Sidecar triggers with 3 clusters: 2 transient, 1 poison | Processes all 3; presents separate ReplayProposal for each transient cluster and quarantine for poison; all gated |
| DLQ-EVAL-04 | Transient cluster where trades FX-000701 through FX-000742 caused state divergence | Includes divergence context from evaluateCanonicalState; elevates priority; recommends reconciliation follow-up |
| DLQ-EVAL-05 | Operator denies replay for transient cluster | Agent logs denial; cluster marked "reviewed-not-replayed"; no replay executed |
| DLQ-EVAL-06 | Malformed trigger payload (missing cluster_id) | Agent logs structured error; does not proceed with triage |

---

## Agentic Design Patterns

| Pattern | Application in this Agent |
|---|---|
| 5 — Tool Use | Calls replayDlqMessage, quarantineMessage, evaluateCanonicalState |
| 11 — Exception Handling | Core function: triaging failed messages |
| 12 — Human-in-the-Loop | All replay and quarantine actions require HITL approval |
| 19 — Prioritization | Clusters ranked by impact, divergence, and age |
| 20 — Exploration | Uses episodic memory of prior triage outcomes to inform classification |

---

## Risk Classification

- **Risk level:** M (replays messages — can re-introduce load or re-trigger
  processing; quarantines remove messages from retry path)
- **Side effects:** Replays DLQ messages (Risk M, gated); quarantines poison
  messages (Risk M, gated)
- **HITL requirement:** Mandatory for ALL actions (replay and quarantine)

---

## MCP Tools Called

| Tool Name | Service | Risk | Purpose |
|---|---|---|---|
| `replayDlqMessage` | `EVENT_STREAM` management | M | Replay transient-failure messages (gated) |
| `quarantineMessage` | `EVENT_STREAM` management | M | Move poison messages to quarantine (gated) |
| `evaluateCanonicalState` | `state-reconciliation-service` | L | Check if DLQ failure caused state divergence |

---

## Python Sidecar Dependency

- **Trigger:** `dlq-cluster-analyzer` sidecar (from
  `06-local-deploy/02-python-sidecars`) — performs stack-trace clustering,
  embedding-based signature grouping, and delivers pre-clustered
  `FailureSignature` envelopes to the `DLQTriageAgent` via webhook.
- **Role:** Detection/clustering (no LLM, no business logic). The sidecar
  is the ONLY trigger for this agent — it is never invoked on-demand by users.
