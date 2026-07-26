# FXOps Helm Charts

Helm values for deploying FXOps microservices, agents, and supporting infrastructure to EKS.

## Structure

```
helm/
├── values-common.yaml              # Shared defaults (probes, OTel, HPA, resources)
├── trade-ingest-service/           # Trade ingestion (RDS, MSK, Redis)
├── trade-lifecycle-service/        # Lifecycle management (RDS, MSK, DocumentDB, Redis)
├── risk-calculation-service/       # Risk engine (RDS, MSK, Redis)
├── eod-processing-service/         # End-of-day batch (RDS, MSK)
├── business-calendar-service/      # Calendar lookups (RDS, Redis)
├── state-reconciliation-service/   # Cross-store reconciliation (all stores)
├── event-sequence-processor/       # Kafka Streams (MSK only)
└── n8n/                            # AI agent workflow engine
```

## Deployment

Each service uses the common values as a base, overridden by service-specific values:

```bash
# Example: deploy trade-ingest-service
helm upgrade --install trade-ingest ./chart/ \
  -f values-common.yaml \
  -f trade-ingest-service/values.yaml \
  -n fxops-services \
  --set image.tag=v1.2.3

# Deploy n8n agents
helm upgrade --install n8n ./chart/ \
  -f values-common.yaml \
  -f n8n/values.yaml \
  -n fxops-agents
```

## Environment Variables

All services reference Terraform outputs via environment variables:
- `${RDS_PROXY_ENDPOINT}` — RDS Proxy connection string
- `${MSK_BOOTSTRAP_BROKERS}` — MSK IAM bootstrap servers
- `${ELASTICACHE_CONFIG_ENDPOINT}` — Redis cluster endpoint
- `${DOCDB_CLUSTER_ENDPOINT}` — DocumentDB cluster endpoint
- `${NEPTUNE_CLUSTER_ENDPOINT}` — Neptune graph endpoint

These are injected via ExternalSecrets or ConfigMaps populated from Terraform outputs.

## Secrets

All credentials sourced from AWS Secrets Manager via External Secrets Operator:
- Redis AUTH token
- DocumentDB master password
- OpenSearch admin password
- n8n encryption key and DB credentials

No plaintext secrets in values files.
