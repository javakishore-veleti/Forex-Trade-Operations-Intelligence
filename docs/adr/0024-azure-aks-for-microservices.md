# ADR-0024: Azure AKS over Azure Container Apps for Microservices

**Status:** Accepted

**Date:** 2024-02-20

## Context

For Azure deployment, the platform's 7 microservices, MCP server, n8n, and sidecars need a container orchestration strategy. The workloads include stateful components (n8n, Kafka Streams processor), latency-sensitive services (trade-ingest), and batch processing (EOD).

Two options were evaluated:

1. **Azure Kubernetes Service (AKS)** — managed Kubernetes with full API compatibility.
2. **Azure Container Apps** — serverless container platform built on Kubernetes (KEDA-based scaling).

## Decision

We adopt **AKS** as the primary compute platform for Azure deployment.

### Implementation

| Configuration | Value |
|--------------|-------|
| Kubernetes version | 1.29+ |
| System node pool | 2× Standard_D4s_v5 (always-on) |
| Services node pool | KEDA autoscaler, Standard_D4s_v5-D8s_v5, spot-eligible |
| Stateful node pool | Standard_D4s_v5, on-demand, tainted for stateful workloads |
| Networking | Azure CNI Overlay + Cilium |
| Ingress | NGINX Ingress Controller + Azure Front Door |

### Workload Distribution

| Workload | Node Pool | Scaling |
|----------|-----------|---------|
| Microservices (7) | Services | KEDA (Kafka lag, HTTP rate) |
| MCP Server | Services | HPA (CPU/memory) |
| n8n | Stateful | Fixed 2 replicas (HA) |
| Kafka Streams processor | Stateful | Fixed (partition-based) |
| Python sidecars | Services | CronJob / event-driven |

## Alternatives Considered

| Alternative | Reason Rejected |
|-------------|-----------------|
| Azure Container Apps | No StatefulSet support; limited volume options for n8n/Kafka; KEDA scaling only (no custom controllers); networking constraints with private endpoints |
| Container Apps + AKS hybrid | Operational complexity of managing two platforms; shared networking challenges |
| Azure Container Instances | No orchestration; suitable for batch jobs only, not persistent services |

## Consequences

### Positive
- Full Kubernetes API — same manifests/Helm charts as AWS EKS deployment (portability)
- StatefulSet support for n8n and Kafka Streams state stores
- Custom resource definitions (CRDs) for operators (cert-manager, external-secrets)
- KEDA integration for event-driven autoscaling (same as Container Apps would provide)
- Azure CNI Overlay reduces IP address consumption

### Negative
- More operational overhead than Container Apps (node upgrades, security patching)
- Cluster management cost even during low-traffic periods
- Team needs Kubernetes expertise across both AWS and Azure

### Mitigations
- AKS auto-upgrade channel for minor versions; planned maintenance windows
- Node auto-provisioning (NAP) reduces node pool management burden
- Shared Helm charts between EKS and AKS deployments — invest once, deploy twice
