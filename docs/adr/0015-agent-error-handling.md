# ADR-0015: Agent Error Handling Strategy

**Status:** Accepted

**Date:** 2024-02-15

## Context

Agent workflows interact with multiple backend services, LLMs, and external systems. Failures are inevitable — services may be temporarily unavailable, LLM calls may timeout, or tool responses may be malformed. The error handling strategy must balance reliability with user experience and avoid silent failures.

Three approaches were evaluated:

1. **Retry + DLQ** — retry failed steps with exponential backoff; after max retries, send to dead-letter queue.
2. **Circuit breaker** — stop calling a failing dependency after threshold; return fast failure to user.
3. **Graceful degradation with partial result** — return whatever information was successfully gathered, clearly marking gaps.

## Decision

We adopt **graceful degradation with partial results** as the primary strategy, supplemented by retry for transient failures and circuit breaker for sustained outages.

### Implementation

| Failure Type | Strategy | Behaviour |
|--------------|----------|-----------|
| Transient (timeout, 503) | Retry 3× with backoff (1s, 2s, 4s) | Transparent to user if succeeds within retries |
| Tool call failure | Graceful degradation | Return partial result with `"gaps": ["risk_data_unavailable"]` |
| LLM call failure | Fallback to T3 model; if fails, return structured error | Never return empty response |
| Sustained outage | Circuit breaker (5 failures in 60s) | Fast-fail for affected tool; notify operator |
| Malformed response | Log + skip step | Continue investigation with available data |

### Example

Trade Lifecycle Reconstruction Agent investigating FX-003299:
1. ✅ Trade events retrieved successfully
2. ❌ Risk calculation service timeout (3 retries exhausted)
3. ✅ Settlement status retrieved successfully

Response to user:
```json
{
  "result": "partial",
  "trade_id": "FX-003299",
  "lifecycle": [...],
  "settlement": "MATCHED",
  "gaps": ["risk_data_unavailable: risk-calculation-service timed out"],
  "recommendation": "Risk data temporarily unavailable. Core lifecycle analysis complete."
}
```

## Alternatives Considered

| Alternative | Reason Rejected |
|-------------|-----------------|
| Retry + DLQ only | User waits too long; DLQ processing is async — not suitable for interactive investigation |
| Circuit breaker only | Too aggressive for intermittent failures; users get empty responses during partial outages |
| Full retry until success | Unacceptable latency for user-facing workflows; could retry indefinitely |

## Consequences

### Positive
- Users always get a response — partial information is better than timeout
- Gaps are explicitly declared — no silent omissions
- Circuit breaker prevents cascading failures to overwhelmed services
- Retry handles common transient network issues transparently

### Negative
- Partial results may lead to incomplete investigations; operators must be trained to check gaps
- Circuit breaker threshold tuning requires production observation data
- Multiple error paths increase workflow complexity

### Mitigations
- Admin Portal highlights gap indicators in red; operator can retry specific gaps
- Circuit breaker thresholds are externalized in Redis (adjustable without redeployment)
- Workflow error paths are covered by golden-set evaluation (ADR-0016)
