# ADR-0032: Agent Evaluation Strategy (Evals)

## Status
Accepted

## Context
AI agents produce non-deterministic outputs. Without systematic evaluation, regressions go undetected until production incidents. The platform needs a testing strategy that works for both development (pre-deploy) and production (runtime monitoring).

## Decision
Adopt a **three-tier evaluation strategy**:

### Tier 1: Golden-Set Regression (pre-deploy, blocking)
- A fixed set of **known-failure scenarios** with expected agent outputs
- Each agent has 6+ golden-set cases defined in its `tasks.md` acceptance scenarios
- Run on every agent workflow change (CI gate)
- Pass criteria: agent produces the correct tool calls, correct reasoning structure, and correct permitted-action selection (not exact text match — structured output match)
- Golden sets live in `Agents/evals/golden-sets/{agent-name}/`

### Tier 2: Shadow Evaluation (pre-deploy, non-blocking)
- On major agent changes, run the new workflow against **production-like synthetic data** in a shadow environment
- Compare outputs against the previous version (diff report)
- Human reviews the diff before promoting to production
- Not blocking — informs the deploy decision, doesn't auto-reject

### Tier 3: Production Monitoring (runtime, alerting)
- Track agent metrics: tool-call success rate, HITL approval rate, response latency, hallucination rate (detected by checking if agent references entities that don't exist in the tool response)
- Alert on: tool-call failure spike, approval rejection rate >20%, response time p95 >30s
- Monthly review of rejected HITL actions to identify agent reasoning failures

### Eval infrastructure:
- `Agents/evals/` directory containing golden-set fixtures per agent
- Eval runner script (`Agents/evals/run-golden-set.sh`) that imports workflow, feeds test cases, asserts structured outputs
- Results stored in `Agents/evals/results/` (JSON, git-tracked for historical comparison)

## Alternatives Considered
- **LLM-as-judge** (use an LLM to evaluate agent outputs) — rejected as primary method because it's non-deterministic; acceptable as supplementary signal for naturalness/coherence
- **Exact text match** — rejected; agent explanations are non-deterministic; only structured outputs (tool calls, actions, classifications) should be asserted
- **No pre-deploy eval** (rely on production monitoring only) — rejected; too risky for M/H-risk agents that gate on human approval
- **A/B testing in production** — rejected for now; requires traffic splitting infrastructure; viable later when agent fleet is mature

## Consequences
- Every agent has a minimum quality bar enforced by golden-set tests
- Regressions are caught before deploy (Tier 1)
- Major changes get human review via shadow evaluation (Tier 2)
- Production issues are detected via metrics, not user complaints (Tier 3)
- The eval infrastructure grows with the agent fleet (new agents = new golden sets)
