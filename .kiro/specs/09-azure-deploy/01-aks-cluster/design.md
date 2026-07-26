# Design Document — AKS Cluster (Container Orchestration on Azure)

> **Stage 2 of 3** (`requirements.md → design.md → tasks.md`). Resolves `CloudTargetBinding`
> for container orchestration → Azure AKS. References concrete Azure services and Bicep/Terraform modules.

## 1. Overview

The platform deploys to **Azure Kubernetes Service (AKS)** with system and user node pools,
Application Gateway Ingress Controller (AGIC) for ingress, and Azure Workload Identity for pod
identity. Infrastructure is defined via **Bicep** templates under `DevOps/Azure/bicep/`.

**Technology Bindings:**

| Technology Role | Azure Service | Configuration |
|---|---|---|
| Container Orchestration | Azure AKS | Kubernetes 1.30, managed control plane |
| Node Compute | VMSS Node Pools | D4s_v5 (services), E4s_v5 (agents/sidecars) |
| Ingress | Application Gateway + AGIC | TLS termination via Key Vault |
| Identity | Azure Workload Identity | Per-service user-assigned managed identity |
| Secrets | Azure Key Vault + CSI Driver | Synced to K8s secrets |
| Certificate | Azure Key Vault Certificates | Auto-renewed TLS certs |

## 2. VNet and Network Layout

```
VNet CIDR: 10.0.0.0/16  (parameterized)

Subnets:
  aks-system:    10.0.1.0/24   ← system node pool
  aks-services:  10.0.2.0/22   ← service node pool (large, pod IPs via Azure CNI)
  aks-agents:    10.0.6.0/24   ← agent/sidecar node pool
  appgw:         10.0.10.0/24  ← Application Gateway subnet
  data:          10.0.20.0/22  ← PostgreSQL, Event Hub PE, Redis PE, Cosmos PE
  nat:           NAT Gateway attached to aks-* subnets for egress
```

Private DNS zones for AKS API server, PostgreSQL, Event Hub, Redis, Cosmos DB.

## 3. AKS Cluster Configuration

```bicep
// DevOps/Azure/bicep/modules/aks/main.bicep (conceptual)
resource aksCluster 'Microsoft.ContainerService/managedClusters@2024-01-01' = {
  name: 'fxops-${environment}'
  location: location
  identity: { type: 'SystemAssigned' }
  properties: {
    kubernetesVersion: '1.30'
    dnsPrefix: 'fxops-${environment}'
    apiServerAccessProfile: { enablePrivateCluster: true }
    networkProfile: {
      networkPlugin: 'azure'
      networkPolicy: 'calico'
      serviceCidr: '172.16.0.0/16'
      dnsServiceIP: '172.16.0.10'
    }
    addonProfiles: {
      ingressApplicationGateway: { enabled: true, config: { applicationGatewayId: appGw.id } }
      azureKeyvaultSecretsProvider: { enabled: true }
      omsagent: { enabled: true, config: { logAnalyticsWorkspaceResourceID: workspace.id } }
    }
    oidcIssuerProfile: { enabled: true }
    securityProfile: { workloadIdentity: { enabled: true } }
  }
}
```

## 4. Node Pools

| Node Pool | VM SKU | Min | Max | Mode | Labels | Taints |
|---|---|---|---|---|---|---|
| `system` | D2s_v5 (2 vCPU, 8 GiB) | 2 | 4 | System | — | `CriticalAddonsOnly=true:NoSchedule` |
| `services` | D4s_v5 (4 vCPU, 16 GiB) | 3 | 10 | User | `workload=services` | — |
| `agents` | E4s_v5 (4 vCPU, 32 GiB) | 1 | 4 | User | `workload=agents` | — |
| `spotsidecars` | D4s_v5 (Spot) | 1 | 6 | User | `workload=sidecars` | `kubernetes.azure.com/scalesetpriority=spot:NoSchedule` |

All pools across 3 availability zones. OS: Ubuntu 22.04 (AKS default).

## 5. Namespaces and Network Policies

```yaml
Namespaces:
  - fxops-services    # 7 Middleware services
  - fxops-portals     # 3 Angular portal deployments (nginx-served)
  - fxops-agents      # n8n instance
  - fxops-sidecars    # 4 Python sidecar deployments
  - fxops-infra       # AGIC, external-secrets, metrics-server
```

Calico NetworkPolicy:
- Default deny all ingress/egress per namespace
- `fxops-agents` → `fxops-services:8080` (MCP tool calls) ALLOW
- `fxops-sidecars` → `fxops-agents:5678` (webhook triggers) ALLOW
- `fxops-services` → data subnet Private Endpoints (5432, 9093, 6380, 27017) ALLOW
- `fxops-portals` → `fxops-services:8080` (API calls) ALLOW

## 6. Azure Workload Identity

Each service gets a User-Assigned Managed Identity + Federated Credential:

| Service | Managed Identity | Azure RBAC Roles |
|---|---|---|
| trade-ingest | `fxops-trade-ingest-<env>` | Key Vault Secrets User, Event Hub Data Sender |
| trade-lifecycle | `fxops-trade-lifecycle-<env>` | Key Vault Secrets User, Event Hub Data Sender/Receiver |
| risk-calculation | `fxops-risk-calculation-<env>` | Key Vault Secrets User, Event Hub Data Sender/Receiver |
| eod-processing | `fxops-eod-processing-<env>` | Key Vault Secrets User, Event Hub Data Sender/Receiver |
| business-calendar | `fxops-business-calendar-<env>` | Key Vault Secrets User |
| state-reconciliation | `fxops-state-reconciliation-<env>` | Key Vault Secrets User, Event Hub Data Receiver |
| event-sequence-processor | `fxops-event-sequence-<env>` | Event Hub Data Sender/Receiver |

Federated credential: AKS OIDC issuer → namespace/service-account → Managed Identity trust.

## 7. Helm Chart Structure

```
DevOps/Azure/helm/
├── charts/
│   └── fxops-base/              ← shared library chart
│       ├── Chart.yaml
│       └── templates/
│           ├── _deployment.tpl
│           ├── _service.tpl
│           ├── _hpa.tpl
│           ├── _serviceaccount.tpl
│           └── _configmap.tpl
├── services/
│   ├── trade-ingest/
│   │   ├── Chart.yaml
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

## 8. Ingress (AGIC + Application Gateway)

```yaml
apiVersion: networking.k8s.io/v1
kind: Ingress
metadata:
  annotations:
    kubernetes.io/ingress.class: azure/application-gateway
    appgw.ingress.kubernetes.io/ssl-redirect: "true"
    appgw.ingress.kubernetes.io/appgw-ssl-certificate: "fxops-tls-cert"
spec:
  tls:
    - hosts: ["fxops.example.com"]
      secretName: fxops-tls-secret
  rules:
    - host: fxops.example.com
      http:
        paths:
          - path: /admin
            backend: { service: { name: portal-admin, port: { number: 80 } } }
          - path: /traderdesk
            backend: { service: { name: portal-traderdesk, port: { number: 80 } } }
          - path: /api/v1
            backend: { service: { name: trade-ingest, port: { number: 8080 } } }
```

## 9. HPA Configuration

Same as AWS — shared library chart template, CPU target 70%, min 2, max configurable, stabilization 300s.

## 10. Tagging Strategy

All Azure resources tagged:
- `project: fxops`
- `environment: <dev|staging|prod>`
- `managed-by: bicep`
- `service: <service-name>` (where applicable)

## 11. Bicep Module Layout

```
DevOps/Azure/bicep/
├── modules/
│   ├── vnet/
│   ├── aks/
│   ├── identity/
│   └── appgateway/
├── environments/
│   ├── dev/
│   │   ├── main.bicep
│   │   └── parameters.json
│   └── prod/
│       ├── main.bicep
│       └── parameters.json
└── main.bicep          ← orchestrator
```
