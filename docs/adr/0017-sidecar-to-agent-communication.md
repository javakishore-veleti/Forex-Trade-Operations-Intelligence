# ADR-0017: Sidecar-to-Agent Communication

**Status:** Accepted

**Date:** 2024-02-16

## Context

Python sidecars (KPI Anomaly Detector, DLQ Cluster Analyzer, Capacity Forecast, Log Normalizer) produce detection results that must trigger agent investigations. The communication pattern must be reliable (detections must not be lost), support backpressure, and maintain the language-boundary separation (sidecars never call Java services directly for business logic).

Three approaches were evaluated:

1. **Webhook POST** — sidecar POSTs detection results to n8n webhook trigger endpoints.
2. **Kafka topic** — sidecar publishes to a Kafka topic; n8n Kafka trigger consumes.
3. **Shared queue** — Redis/RabbitMQ queue as intermediary.

## Decision

We adopt **webhook POST** as the primary communication mechanism from sidecars to agents, with Kafka as the durable audit trail.

### Implementation

- Each sidecar POSTs detection results to an n8n webhook URL specific to the target agent workflow.
- Webhook payload follows the MCP_Tool_Contract envelope:
  ```json
  {
    "detection_type": "kpi_anomaly",
    "severity": "HIGH",
    "affected_entities": ["FX-005192", "FX-005193"],
    "metrics": {"settlement_rate_drop": 0.23},
    "timestamp": "2024-02-16T10:15:00Z",
    "sidecar_id": "kpi-anomaly-detector-01"
  }
  ```
- Sidecar also publishes the same payload to Kafka topic `fx.sidecar.detections` for audit and replay.
- If webhook POST fails (n8n unavailable), the sidecar retries 3× with exponential backoff, then falls back to Kafka-only (agent can poll on recovery).

### Routing

| Sidecar | Webhook Target | Agent |
|---------|---------------|-------|
| KPI Anomaly Detector | `/webhook/kpi-anomaly` | Supervisor → appropriate specialist |
| DLQ Cluster Analyzer | `/webhook/dlq-cluster` | DLQ Triage Agent |
| Capacity Forecast | `/webhook/capacity-alert` | Supervisor → Capacity Planning Agent |
| Log Normalizer | (no trigger — passive) | N/A — writes to Elasticsearch |

## Alternatives Considered

| Alternative | Reason Rejected |
|-------------|-----------------|
| Kafka topic only | n8n Kafka trigger has higher latency (polling interval); webhook provides near-instant triggering |
| Shared Redis queue | Adds coupling through shared infrastructure; Redis not designed for reliable messaging |
| gRPC streaming | Over-engineered for infrequent detection events (seconds to minutes between detections) |

## Consequences

### Positive
- Near-instant agent triggering (< 100ms from detection to workflow start)
- Simple implementation — HTTP POST is universally supported
- Kafka audit trail provides replay capability for missed detections
- Clear contract boundary via MCP_Tool_Contract envelope

### Negative
- Webhook URL must be configured in each sidecar (coupling to n8n deployment)
- n8n webhook availability is a dependency — if n8n is down, real-time triggering fails
- No built-in backpressure — rapid-fire detections could overwhelm n8n

### Mitigations
- Webhook URLs externalized in environment variables; updated via config management
- Kafka fallback ensures no detection is permanently lost
- Sidecar-side rate limiting: max 10 webhook calls per minute per sidecar (batch if exceeded)
