# ADR-0019: AWS EKS Node Strategy — Karpenter Autoscaling

**Status:** Accepted

**Date:** 2024-02-18

## Context

The platform's 7 microservices, MCP server, n8n, and supporting infrastructure need a Kubernetes compute strategy on AWS. Workload characteristics vary significantly: trade-ingest has bursty traffic during market hours, EOD processing runs heavy batch jobs at close, while risk-calculation needs consistent low-latency.

Three approaches were evaluated:

1. **Managed node groups** — pre-defined instance types with Cluster Autoscaler.
2. **Fargate** — serverless, per-pod billing, no node management.
3. **Karpenter** — just-in-time node provisioning with flexible instance selection.

## Decision

We adopt **Karpenter** as the primary autoscaling strategy for EKS, with a small managed node group for system workloads.

### Implementation

| Workload Type | Compute Strategy | Instance Selection |
|---------------|-----------------|-------------------|
| System (CoreDNS, metrics, Karpenter itself) | Managed node group (2× m6i.large, always-on) | Fixed |
| Microservices (trade-ingest, lifecycle, risk) | Karpenter NodePool `services` | m6i/m7i.large-2xlarge, spot-eligible |
| Stateful (n8n, Kafka Connect) | Karpenter NodePool `stateful` | m6i.xlarge-2xlarge, on-demand only |
| Batch (EOD processing) | Karpenter NodePool `batch` | c6i/c7i.2xlarge-4xlarge, spot preferred |

### Karpenter NodePool Example

```yaml
apiVersion: karpenter.sh/v1beta1
kind: NodePool
metadata:
  name: services
spec:
  template:
    spec:
      requirements:
        - key: karpenter.k8s.aws/instance-family
          operator: In
          values: ["m6i", "m7i"]
        - key: karpenter.k8s.aws/instance-size
          operator: In
          values: ["large", "xlarge", "2xlarge"]
        - key: karpenter.sh/capacity-type
          operator: In
          values: ["spot", "on-demand"]
  limits:
    cpu: 200
  disruption:
    consolidationPolicy: WhenUnderutilized
```

## Alternatives Considered

| Alternative | Reason Rejected |
|-------------|-----------------|
| Managed node groups only | Cluster Autoscaler is slower (minutes vs seconds); instance type locked per group |
| Fargate only | No DaemonSet support (needed for OTel collector, Fluent Bit); Kafka/n8n need persistent volumes |
| Fargate for microservices + managed for stateful | Fargate cold starts (30-60s) unacceptable for latency-sensitive trade processing |

## Consequences

### Positive
- Sub-minute node provisioning for traffic spikes (market open, flash events)
- Cost optimization via spot for stateless workloads + consolidation for off-hours
- Flexible instance selection — Karpenter picks cheapest available instance matching constraints
- No node group scaling lag during EOD batch processing

### Negative
- Karpenter is newer than Cluster Autoscaler; team needs operational experience
- Spot interruptions require robust pod disruption budgets and graceful shutdown
- Karpenter itself needs a stable managed node group to run on

### Mitigations
- System managed node group ensures Karpenter control plane is always available
- Pod disruption budgets enforce min-available for all stateful services
- Spot interruption handler configured to drain nodes gracefully (2-minute warning)
