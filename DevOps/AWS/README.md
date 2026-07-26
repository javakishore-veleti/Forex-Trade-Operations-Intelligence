# FXOps AWS Infrastructure

Production-ready AWS infrastructure for the Forex-Trade-Operations-Intelligence platform, deployed via Terraform and Helm.

## Architecture

| Component | AWS Service | Access Pattern |
|-----------|-------------|----------------|
| Container Orchestration | EKS (K8s 1.30) | Private endpoint, managed node groups |
| Relational Store | RDS PostgreSQL 16 | Via RDS Proxy, TLS + IAM auth |
| Event Stream | MSK Kafka 3.6 | SASL/IAM over TLS, port 9098 |
| Cache | ElastiCache Redis 7.1 | Cluster mode, TLS + AUTH token |
| Document Store | DocumentDB 7.0 | MongoDB-compatible, TLS |
| Graph Store | Neptune 1.3 | openCypher, IAM auth |
| Observability Logging | OpenSearch 2.11 | VPC-only, FGAC |

## Prerequisites

- Terraform >= 1.6.0
- AWS CLI v2 configured with appropriate credentials
- Helm >= 3.12
- kubectl configured for EKS access

## Quick Start

```bash
# Initialize Terraform
cd terraform/
cp terraform.tfvars.example terraform.tfvars
# Edit terraform.tfvars with your environment values

terraform init
terraform plan -out=plan.out
terraform apply plan.out

# Deploy Helm charts
cd ../helm/
helm upgrade --install trade-ingest ./trade-ingest-service/ \
  -f values-common.yaml \
  -f trade-ingest-service/values.yaml \
  -n fxops-services
```

## Security

- All data stores in private subnets (no public endpoints)
- TLS enforced on all connections
- Credentials in AWS Secrets Manager (90-day rotation)
- IRSA for pod-level IAM (no static credentials)
- KMS CMK for encryption at rest

## Tagging

All resources tagged: `project=fxops`, `environment=<env>`, `managed-by=terraform`

## Synthetic Data Policy

All identifiers use `FX-` prefix. No real financial data. Account ID `123456789012` is a placeholder.
