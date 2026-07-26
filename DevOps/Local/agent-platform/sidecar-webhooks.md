# Sidecar Webhook Mapping

This document maps each Python sidecar to its webhook URL and the target n8n workflow it triggers.

## Webhook Endpoints

| Sidecar | Webhook URL | Target Workflow | Trigger Condition |
|---------|-------------|-----------------|-------------------|
| kpi-anomaly-detector | `http://fxops-n8n:5678/webhook/kpi-anomaly` | KPI Anomaly Triage | Z-score exceeds `DETECTION_THRESHOLD` (default: 2.5σ) |
| dlq-cluster-analyzer | `http://fxops-n8n:5678/webhook/dlq-cluster` | DLQ Cluster Analysis | New significant cluster ≥ `MIN_CLUSTER_SIZE` (default: 3) |
| capacity-forecast-model | `http://fxops-n8n:5678/webhook/capacity-shortfall` | Capacity Shortfall Alert | Estimated shortfall > `SHORTFALL_THRESHOLD_MINUTES` (default: 30 min) |
| log-normalizer | `http://fxops-n8n:5678/webhook/log-normalized` | Log Normalization Batch | Structured facts ready for downstream processing |

## Payload Formats

### kpi-anomaly-detector

```json
{
  "kpi_name": "trade_processing_latency_ms",
  "value": 450.5,
  "mean": 120.3,
  "std_dev": 25.1,
  "z_score": 13.15,
  "is_anomaly": true,
  "timestamp": "2025-07-25T10:30:15.123Z"
}
```

### dlq-cluster-analyzer

```json
{
  "clusters": [
    {
      "cluster_id": "a1b2c3d4e5f6g7h8",
      "representative_trace": "java.lang.NullPointerException...",
      "count": 15,
      "first_seen": "2025-07-25T09:00:00Z",
      "last_seen": "2025-07-25T10:30:00Z",
      "sample_trade_ids": ["FX-000001", "FX-000015", "FX-000042"]
    }
  ]
}
```

### capacity-forecast-model

```json
{
  "branch_id": "branch-APAC",
  "estimated_completion_minutes": 95.5,
  "cutoff_minutes_remaining": 60.0,
  "shortfall_minutes": 35.5,
  "confidence": 0.85,
  "is_at_risk": true,
  "timestamp": "2025-07-25T10:30:15Z"
}
```

### log-normalizer

```json
{
  "facts": [
    {
      "timestamp": "2025-07-25T10:30:15Z",
      "level": "ERROR",
      "service": "trade-lifecycle",
      "trade_id": "FX-000042",
      "exception_class": "NullPointerException",
      "message": "Failed to process settlement",
      "correlation_id": "abc12345-def6-7890"
    }
  ]
}
```

## Environment Variables

Each sidecar reads its webhook URL from the `WEBHOOK_URL` environment variable.
See individual `config.py` files for all available settings.

## Network

All sidecars and n8n are on the `fxops-agent-net` Docker network.
Sidecars use the container name `fxops-n8n` to reach the n8n instance.
