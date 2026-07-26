# ADR-0024: Python Sidecars as Webhook Triggers to n8n

## Status
Accepted

## Context
Four Python sidecars perform continuous detection and analysis:
- **KPI Anomaly Detector** — detects metric deviations (e.g., trade volume drop > 2σ)
- **DLQ Cluster Analyzer** — clusters DLQ entries by similarity to identify systemic issues
- **Capacity Forecast** — predicts resource exhaustion based on trend analysis
- **Log Normalizer** — extracts structured fields from unstructured log entries

When a sidecar detects something actionable, it must notify the appropriate n8n agent workflow
(e.g., DLQ Triage Agent, Canary Probe Agent) to initiate investigation or remediation.

The communication mechanism must be:
- Low-latency (detection → agent activation within seconds)
- Reliable (detection must not be lost)
- Simple to implement in Python (sidecars are lightweight)
- Compatible with n8n's trigger mechanisms

## Decision
Sidecars send **HTTP POST webhook triggers** to n8n workflow webhook endpoints when detections occur.

```python
# In kpi_anomaly_detector/notifier.py
async def notify_agent(detection: AnomalyDetection) -> None:
    payload = {
        "detectionId": detection.id,
        "detectorType": "KPI_ANOMALY",
        "severity": detection.severity.value,
        "summary": detection.summary,
        "evidence": detection.evidence_envelope(),
        "timestamp": detection.detected_at.isoformat(),
    }
    async with httpx.AsyncClient() as client:
        await client.post(
            f"{N8N_BASE_URL}/webhook/sidecar-detection",
            json=payload,
            timeout=5.0,
        )
```

n8n workflow starts with a Webhook Trigger node that routes to the appropriate agent based on
`detectorType`. The payload follows a shared `DetectionEnvelope` schema.

## Alternatives Considered

| Alternative | Why rejected |
|-------------|-------------|
| **Kafka producer (sidecar → topic → n8n consumer)** | n8n's Kafka trigger node has limited reliability guarantees; adds Kafka client dependency to lightweight Python sidecars; over-engineered for ~100 detections/day |
| **Direct n8n polling (n8n polls sidecar endpoint)** | Inverts the control flow; sidecars would need to buffer detections and serve a poll endpoint; adds state management; latency = poll interval |
| **Redis Pub/Sub** | Adds Redis client to sidecars; fire-and-forget semantics mean lost detections if n8n is restarting; no built-in retry |
| **gRPC streaming** | Heavy infrastructure for low-volume detection signals; n8n has no native gRPC trigger; requires proxy layer |

## Consequences

### Positive
- Simplest integration path — sidecars need only `httpx` (already a dependency)
- n8n Webhook Trigger is a first-class node — zero custom plugin development
- Latency < 1 second from detection to agent activation
- `DetectionEnvelope` schema shared across all sidecars ensures uniform agent parsing
- Webhook URL is the only configuration needed in sidecars (`N8N_WEBHOOK_URL` env var)

### Negative
- Webhook delivery is fire-and-forget — if n8n is down, detection is lost
- No backpressure mechanism — a burst of detections floods n8n's webhook queue
- Sidecar must handle HTTP timeouts and connection failures gracefully
- Webhook URL coupling — changing n8n's webhook path requires sidecar reconfiguration

### Mitigations
- Retry with exponential backoff (3 attempts, 1s/2s/4s) before logging detection to local file as fallback
- n8n webhook queue handles burst; Grafana alert fires if sidecar retry rate exceeds threshold
- Docker Compose health check ensures n8n is running before sidecars start (dependency ordering)
- Webhook URL centralized in `.env` file used by both Docker Compose and sidecar config
