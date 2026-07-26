# ADR-0012: Agent Model Tier Allocation

**Status:** Accepted

**Date:** 2024-02-12

## Context

The platform runs 34 agents with varying complexity requirements. Some agents (e.g., supervisor routing) need advanced reasoning, while others (e.g., Canary Probe health check) need only simple structured output. Using a single expensive model for all agents wastes budget; using the cheapest model everywhere degrades quality for complex tasks.

Three strategies were evaluated:

1. **Fixed per-agent assignment** — each agent has a hardcoded model in its workflow configuration.
2. **Dynamic routing** — a meta-layer selects the model based on task complexity signals.
3. **Single model for all** — one model across the platform for simplicity.

## Decision

We adopt **fixed per-agent assignment with tier-based grouping**, where agents are classified into three model tiers at design time:

| Tier | Model Class | Use Cases | Example Agents |
|------|-------------|-----------|----------------|
| T1 (Reasoning) | GPT-4o / Claude Sonnet | Multi-step analysis, root cause investigation, complex summarization | Trade Lifecycle Reconstruction, Settlement Failure Diagnosis |
| T2 (Capable) | GPT-4o-mini / Claude Haiku | Structured extraction, classification, moderate reasoning | Supervisor routing, DLQ Triage, Risk Explanation |
| T3 (Fast) | GPT-4o-mini / local model | Template filling, status checks, simple transformations | Canary Probe, Heartbeat Monitor, Log Query |

### Implementation

- Each n8n agent workflow specifies its tier in the workflow description metadata.
- n8n credential aliases (`llm-tier1`, `llm-tier2`, `llm-tier3`) abstract the actual model provider.
- Tier assignment is documented in the agent spec's `design.md` and reviewed during spec approval.
- Cost budgets are set per tier: T1 ≤ $0.05/call, T2 ≤ $0.01/call, T3 ≤ $0.002/call.

### Example

Supervisor receives "explain the risk exposure for book FX-BOOK-EU-01":
- Supervisor (T2) classifies intent → routes to Risk Explanation Agent (T2)
- Risk Explanation Agent calls `risk-calculation-service` for data, then uses T2 model to format the explanation.

## Alternatives Considered

| Alternative | Reason Rejected |
|-------------|-----------------|
| Dynamic routing | Adds meta-routing complexity; task-complexity estimation is itself an LLM call; premature optimization |
| Single model | Cost-prohibitive at scale; T1 model for 34 agents × high call volume exceeds budget |
| Per-call dynamic | Non-deterministic behavior makes evaluation and debugging harder |

## Consequences

### Positive
- Predictable cost per agent — budget planning is straightforward
- Simpler debugging — model behavior is consistent per agent
- Credential alias indirection allows model swaps without workflow changes
- Tier assignment is a design-time decision with team review

### Negative
- Tier assignment may be suboptimal for edge cases (some calls need more reasoning than tier allows)
- Model upgrades require reviewing all agents in affected tier
- Three credential sets to manage per environment

### Mitigations
- Evaluation suite (ADR-0016) catches quality degradation after model changes
- Agents can escalate to supervisor if T2/T3 response quality is below threshold
- Quarterly tier review based on accuracy metrics and cost data
