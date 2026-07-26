# ADR-0009: Agent Supervisor Routing Strategy

**Status:** Accepted

**Date:** 2024-02-10

## Context

The supervisor agent must classify incoming requests and route them to the appropriate specialized agent (e.g., Trade Lifecycle Reconstruction, DLQ Triage, Canary Probe). Routing accuracy directly impacts user experience and system safety — a misrouted high-risk action could bypass required HITL gates.

Three routing approaches were evaluated:

1. **LLM intent classification** — send the user utterance to an LLM with a system prompt listing available agents and their descriptions.
2. **Keyword matching** — regex/keyword rules map known patterns to agents (e.g., "DLQ" → DLQ Triage Agent).
3. **NLU classifier** — a fine-tuned lightweight model (e.g., distilbert) trained on labeled routing examples.

## Decision

We adopt **LLM intent classification** as the primary routing mechanism, with a keyword-based fast-path for unambiguous patterns.

### Implementation

- The supervisor n8n workflow sends the user message to the LLM with a structured prompt containing agent descriptions and permitted-action metadata.
- The LLM returns a JSON envelope: `{"agent": "trade-lifecycle-reconstruction", "confidence": 0.92, "intent_summary": "..."}`.
- If confidence < 0.7, the supervisor asks a clarifying question before routing.
- A keyword fast-path short-circuits LLM calls for unambiguous triggers (e.g., message contains "DLQ" and "triage" → DLQ Triage Agent directly).

### Example

User: "Why did trade FX-004521 settle late?"
→ LLM classifies: `trade-lifecycle-reconstruction` (confidence 0.95)
→ Routed to Trade Lifecycle Reconstruction Agent.

## Alternatives Considered

| Alternative | Reason Rejected |
|-------------|-----------------|
| Keyword matching only | Too brittle for natural language; fails on novel phrasing or compound requests |
| NLU classifier | Requires training data maintenance, adds ML infra dependency; overkill for 34-agent catalogue |
| Hybrid NLU + LLM | Complexity not justified given LLM accuracy on structured routing prompts |

## Consequences

### Positive
- Handles novel phrasing and compound queries gracefully
- No training data maintenance burden
- Keyword fast-path reduces latency and cost for obvious routes
- Confidence threshold prevents silent misrouting

### Negative
- LLM call adds 200-800ms latency per routing decision
- Model cost per routing call (~$0.001-0.003 per classification)
- Prompt drift risk if agent catalogue changes without updating supervisor prompt

### Mitigations
- Keyword fast-path handles 40-60% of requests without LLM call
- Supervisor prompt is generated from agent registry metadata at deploy time
- Routing accuracy tracked via golden-set evaluation (see ADR-0016)
