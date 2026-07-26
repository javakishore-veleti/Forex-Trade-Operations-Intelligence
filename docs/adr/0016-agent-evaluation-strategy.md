# ADR-0016: Agent Evaluation Strategy

**Status:** Accepted

**Date:** 2024-02-15

## Context

Agent quality must be measured and tracked over time. Model upgrades, prompt changes, and tool schema modifications can silently degrade agent performance. The evaluation strategy must detect regressions before they reach production and provide ongoing quality metrics.

Three approaches were evaluated:

1. **Golden-set regression** — a curated set of test cases with expected outputs; run before every deployment.
2. **A/B testing** — split live traffic between agent versions; compare outcomes statistically.
3. **Production shadow** — new agent version runs in parallel; outputs compared but not served to users.

## Decision

We adopt **golden-set regression testing** as the primary evaluation mechanism, with production shadow runs for major version changes.

### Implementation

- Each agent maintains a `golden-set.json` file containing 15-30 test cases with:
  - Input trigger (e.g., user message, sidecar alert)
  - Expected routing decision (for supervisor)
  - Expected tool calls (ordered)
  - Expected output structure assertions
  - Acceptable quality thresholds (e.g., similarity > 0.85 for free-text outputs)

- **CI pipeline**: golden-set tests run on every PR that modifies agent workflows or prompts.
- **Quality metrics**: accuracy (exact match for structured), ROUGE-L (for summaries), tool-call precision/recall.
- **Production shadow**: for T1 model changes, the new model runs in parallel for 48 hours. Outputs are compared; if quality delta > 5%, the change is blocked.

### Example

Golden-set entry for Trade Lifecycle Reconstruction Agent:
```json
{
  "id": "gs-tlr-007",
  "input": "Why was trade FX-006721 stuck in PENDING for 3 hours?",
  "expected_tools": ["get-trade-lifecycle", "get-matching-status"],
  "expected_output_contains": ["matching_engine", "timeout"],
  "quality_threshold": 0.85
}
```

## Alternatives Considered

| Alternative | Reason Rejected |
|-------------|-----------------|
| A/B testing only | Requires significant live traffic volume; hard to measure for low-frequency agents; risk of degraded experience for B group |
| Production shadow only | Resource-intensive (double compute); no pre-deployment gate |
| Manual QA review | Does not scale; inconsistent across reviewers; blocks deployment velocity |
| LLM-as-judge only | Adds LLM variability to evaluation itself; not deterministic enough for CI gates |

## Consequences

### Positive
- Pre-deployment regression gate prevents quality degradation from reaching users
- Golden sets serve as living documentation of expected agent behavior
- Deterministic evaluation — no randomness in CI pass/fail decisions
- Production shadow validates real-world performance without user risk

### Negative
- Golden-set maintenance burden — test cases must evolve as agents gain capabilities
- Golden sets may not cover all edge cases (coverage gap)
- Production shadow doubles compute cost during evaluation windows

### Mitigations
- Golden-set coverage metrics tracked per agent; gaps flagged in quarterly review
- Shadow runs limited to 48 hours for T1 changes only — bounded cost
- Flaky tests (non-deterministic LLM output) use similarity thresholds rather than exact match
