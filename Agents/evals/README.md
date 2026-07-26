# Agent Evaluation Framework

> Per [ADR-0032](../../docs/adr/0032-agent-evaluation-strategy.md)

## Three-Tier Evaluation Strategy

### Tier 1: Golden-Set Regression (pre-deploy, blocking)

Fixed known-failure scenarios with expected agent outputs. Each agent has 3–6+
golden-set cases. Run on every agent workflow change (CI gate).

**Pass criteria:** agent produces correct tool calls, correct reasoning
structure, and correct permitted-action selection. Not exact text match —
structured output match on tool calls, classifications, and actions.

Golden sets live in `Agents/evals/golden-sets/{agent-name}/cases.json`.

### Tier 2: Shadow Evaluation (pre-deploy, non-blocking)

On major agent changes, run the new workflow against production-like synthetic
data in a shadow environment. Compare outputs against the previous version (diff
report). Human reviews the diff before promoting.

### Tier 3: Production Monitoring (runtime, alerting)

Track agent metrics:
- Tool-call success rate
- HITL approval rate
- Response latency (p95 < 30s)
- Hallucination rate (agent references non-existent entities)

Alert thresholds:
- Tool-call failure spike
- Approval rejection rate > 20%
- Response time p95 > 30s

## Directory Structure

```
Agents/evals/
├── README.md                          ← this file
├── run-golden-set.sh                  ← eval runner script
├── golden-sets/
│   ├── supervisor-agent/cases.json
│   ├── trade-lifecycle-agent/cases.json
│   ├── canary-probe-agent/cases.json
│   ├── dlq-triage-agent/cases.json
│   ├── risk-explainability-agent/cases.json
│   ├── eod-readiness-agent/cases.json
│   └── state-divergence-agent/cases.json
└── results/                           ← JSON results (git-tracked)
```

## Running Golden-Set Evals

```bash
# Run all golden sets
./Agents/evals/run-golden-set.sh

# Run for a specific agent
./Agents/evals/run-golden-set.sh supervisor-agent

# Run and save results
./Agents/evals/run-golden-set.sh --save-results
```

## Adding a New Agent's Golden Set

1. Create `Agents/evals/golden-sets/{agent-name}/cases.json`
2. Define 3–6 test cases (see existing agents for format)
3. Each case specifies:
   - `name` — descriptive test name
   - `input` — trigger payload (synthetic FX- identifiers only)
   - `expectedToolCalls` — ordered list of tools the agent should invoke
   - `expectedOutputContains` — partial match on structured output fields
   - `expectedRiskGate` — whether the action requires HITL approval

## Synthetic Data Policy

All test data uses `FX-` prefixed identifiers. No real financial institution,
person, or confidential data is used in any fixture.
