# Design Document — EKS Cluster (Container Orchestration on AWS)

> **Stage 2 of 3** (`requirements.md → design.md → tasks.md`). Resolves `CloudTargetBinding`
> for container orchestration → AWS EKS. References concrete AWS services and Terraform modules.

## 1. Overview

The platform deploys to **Amazon EKS** (Elastic Kubernetes Service) with managed node groups,
AWS Load Balancer Controller for ingress, and IAM Roles for Service Accounts (IRSA) for pod identity.
Infrastructure is defined via **Terraform** modules under `DevOps/AWS/terraform/`.

**Technology Bindings:**

| Technology Role | AWS Service | Configuration |
|---|---|---|
| Container Orchestration | Amazon EKS | Kubernetes 1.30, managed control plane |
| Node Compute | EC2 Managed Node Groups | m7g.xlarge (services), r7g.large (agents/sidecars) |
| Ingress | AWS Load Balancer Controller + ALB | TLS termination via ACM |
| Identity | IAM Roles for Service Accounts (IRSA) | Per-service IAM role |
| Secrets | AWS Secrets Manager + External Secrets Operator | Synced to K8s secrets |
| Certificate | AWS Certificate Manager (ACM) | Auto-renewed TLS certs |

## 2. VPC and Network Layout

```
VPC CIDR: 10.0.0.0/16  (placeholder — replace per account)

Public subnets  (3 AZs): 10.0.1.0/24, 10.0.2.0/24, 10.0.3.0/24   ← NAT GW, ALB
Private subnets (3 AZs): 10.0.10.0/24, 10.0.11.0/24, 10.0.12.0/24 ← EKS nodes
Data subnets    (3 AZs): 10.0.20.0/24, 10.0.21.0/24, 10.0.22.0/24 ← RDS, MSK, ElastiCache, etc.

Pod CIDR: 10.1.0.0/16 (VPC CNI custom networking)
Service CIDR: 172.20.0.0/16
```

NAT Gateways in each public subnet for HA egress. No internet gateway route in private/data subnets.

## 3. EKS Cluster Configuration

```hcl
# DevOps/AWS/terraform/modules/eks/main.tf (conceptual)
cluster_name    = "fxops-${var.environment}"
cluster_version = "1.30"
encryption_config {
  provider { key_arn = var.kms_key_arn }   # envelope encryption for secrets
  resources = ["secrets"]
}
enabled_cluster_log_types = ["api", "audit", "authenticator", "controllerManager", "scheduler"]
```

## 4. Node Groups

| Node Group | Instance Type | Min | Max | Labels | Taints |
|---|---|---|---|---|---|
| `services` | m7g.xlarge (4 vCPU, 16 GiB) | 3 | 10 | `workload=services` | — |
| `agents` | r7g.large (2 vCPU, 16 GiB) | 1 | 4 | `workload=agents` | — |
| `spot-sidecars` | m7g.large (spot) | 1 | 6 | `workload=sidecars` | `spot=true:PreferNoSchedule` |

All nodes in private subnets. AMI: Amazon Linux 2023 EKS-optimized (ARM64 for Graviton).

## 5. Namespaces and Network Policies

```yaml
# Namespaces created by Helm/Terraform
- fxops-services    # 7 Middleware services
- fxops-portals     # 3 Angular portal deployments (nginx-served)
- fxops-agents      # n8n instance
- fxops-sidecars    # 4 Python sidecar deployments
- fxops-infra       # ALB controller, external-secrets-operator, metrics-server
```

NetworkPolicy (Calico/VPC CNI network policy):
- `fxops-agents` → `fxops-services` on port 8080 (MCP tool calls) ONLY
- `fxops-sidecars` → `fxops-agents` on port 5678 (webhook triggers) ONLY
- `fxops-services` → data subnets (RDS 5432, MSK 9094, ElastiCache 6379, DocumentDB 27017, Neptune 8182)
- Default deny all other cross-namespace traffic

## 6. IAM Roles for Service Accounts (IRSA)

Each service gets a dedicated IAM role:

| Service | IAM Role | Permissions |
|---|---|---|
| trade-ingest | `fxops-trade-ingest-<env>` | RDS connect, MSK produce, Secrets Manager read |
| trade-lifecycle | `fxops-trade-lifecycle-<env>` | RDS connect, MSK produce/consume, DocumentDB connect |
| risk-calculation | `fxops-risk-calculation-<env>` | RDS connect, MSK produce/consume, ElastiCache connect |
| eod-processing | `fxops-eod-processing-<env>` | RDS connect, MSK produce/consume |
| business-calendar | `fxops-business-calendar-<env>` | RDS connect, ElastiCache connect |
| state-reconciliation | `fxops-state-reconciliation-<env>` | RDS, DocumentDB, ElastiCache, MSK, Neptune connect |
| shared (event-sequence) | `fxops-event-sequence-<env>` | MSK produce/consume |

Trust policy: `sts:AssumeRoleWithWebIdentity` scoped to EKS OIDC provider + namespace + service account.

## 7. Helm Chart Structure

```
DevOps/AWS/helm/
├── charts/
│   └── fxops-base/              ← shared library chart (templates only, no values)
│       ├── Chart.yaml
│       └── templates/
│           ├── _deployment.tpl
│           ├── _service.tpl
│           ├── _hpa.tpl
│           ├── _serviceaccount.tpl
│           └── _configmap.tpl
├── services/
│   ├── trade-ingest/
│   │   ├── Chart.yaml           ← depends on fxops-base
│   │   └── values-{dev,prod}.yaml
│   ├── trade-lifecycle/
│   ├── risk-calculation/
│   ├── eod-processing/
│   ├── business-calendar/
│   ├── state-reconciliation/
│   └── event-sequence-processor/
├── portals/
│   ├── admin/
│   ├── traderdesk/
│   └── fxtradeblotter/
└── platform/
    ├── n8n/
    └── ingress/
```

## 8. Ingress (AWS Load Balancer Controller)

```yaml
apiVersion: networking.k8s.io/v1
kind: Ingress
metadata:
  annotations:
    alb.ingress.kubernetes.io/scheme: internet-facing
    alb.ingress.kubernetes.io/certificate-arn: arn:aws:acm:<REGION>:<ACCOUNT_ID>:certificate/<CERT_ID>
    alb.ingress.kubernetes.io/ssl-policy: ELBSecurityPolicy-TLS13-1-2-2021-06
    alb.ingress.kubernetes.io/target-type: ip
spec:
  rules:
    - host: fxops.<DOMAIN_PLACEHOLDER>
      http:
        paths:
          - path: /admin
            backend: { service: { name: portal-admin, port: { number: 80 } } }
          - path: /api/v1
            backend: { service: { name: trade-ingest, port: { number: 8080 } } }
```

## 9. HPA Configuration

```yaml
# Template in fxops-base/_hpa.tpl
apiVersion: autoscaling/v2
kind: HorizontalPodAutoscaler
spec:
  minReplicas: {{ .Values.hpa.minReplicas | default 2 }}
  maxReplicas: {{ .Values.hpa.maxReplicas | default 8 }}
  behavior:
    scaleDown:
      stabilizationWindowSeconds: 300
  metrics:
    - type: Resource
      resource: { name: cpu, target: { type: Utilization, averageUtilization: 70 } }
```

## 10. Tagging Strategy

All AWS resources tagged:
- `project: fxops`
- `environment: <dev|staging|prod>`
- `managed-by: terraform`
- `service: <service-name>` (where applicable)

## 11. Terraform Module Layout

```
DevOps/AWS/terraform/
├── modules/
│   ├── vpc/
│   ├── eks/
│   ├── irsa/
│   └── alb-controller/
├── environments/
│   ├── dev/
│   │   ├── main.tf
│   │   ├── variables.tf
│   │   └── terraform.tfvars
│   └── prod/
└── backend.tf
```
