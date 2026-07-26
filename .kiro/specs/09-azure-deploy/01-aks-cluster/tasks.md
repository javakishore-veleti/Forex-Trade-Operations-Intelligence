# Tasks — AKS Cluster (Container Orchestration on Azure)

> **Stage 3 of 3** (`requirements.md → design.md → tasks.md`). Execute top-to-bottom;
> each task is atomic and independently verifiable. Mark `[x]` as completed.

## 0. Scaffold

- [ ] 0.1 Create `DevOps/Azure/bicep/modules/vnet/` with `main.bicep`, `parameters.json`. (§2, Req 1)
- [ ] 0.2 Create `DevOps/Azure/bicep/modules/aks/` with `main.bicep`, `parameters.json`. (§3, Req 1)
- [ ] 0.3 Create `DevOps/Azure/bicep/modules/identity/` with `main.bicep`. (§6, Req 4)
- [ ] 0.4 Create `DevOps/Azure/bicep/modules/appgateway/` with `main.bicep`. (§8, Req 6)
- [ ] 0.5 Create `DevOps/Azure/bicep/environments/dev/` with `main.bicep`, `parameters.json`. (§11)
- [ ] 0.6 Create `DevOps/Azure/bicep/environments/prod/` with same structure. (§11)
- [ ] 0.7 Create `DevOps/Azure/bicep/main.bicep` orchestrator referencing modules. (§11)

## 1. VNet Module (Req 1)

- [ ] 1.1 Define VNet resource with CIDR `10.0.0.0/16` (parameterized). Enable DNS.
- [ ] 1.2 Define `aks-system` subnet (10.0.1.0/24) for system node pool.
- [ ] 1.3 Define `aks-services` subnet (10.0.2.0/22) for service node pool with Azure CNI pod IPs.
- [ ] 1.4 Define `aks-agents` subnet (10.0.6.0/24) for agent/sidecar node pool.
- [ ] 1.5 Define `appgw` subnet (10.0.10.0/24) for Application Gateway.
- [ ] 1.6 Define `data` subnet (10.0.20.0/22) for managed data service Private Endpoints.
- [ ] 1.7 Create NAT Gateway and associate with aks-* subnets for egress.
- [ ] 1.8 Output VNet ID, subnet resource IDs. **Verify:** `az bicep build` succeeds.

## 2. AKS Cluster Module (Req 1, 2)

- [ ] 2.1 Define AKS cluster resource with version `1.30`, private API server, Azure CNI + Calico network policy.
- [ ] 2.2 Configure OIDC issuer and Workload Identity security profile.
- [ ] 2.3 Enable AKS addons: AGIC, Key Vault CSI driver, Container Insights.
- [ ] 2.4 Define `system` node pool: D2s_v5, min=2, max=4, CriticalAddonsOnly taint. (§4)
- [ ] 2.5 Define `services` node pool: D4s_v5, min=3, max=10, zones [1,2,3]. (Req 2, §4)
- [ ] 2.6 Define `agents` node pool: E4s_v5, min=1, max=4, zones [1,2,3]. (Req 2, §4)
- [ ] 2.7 Define `spotsidecars` node pool: D4s_v5 Spot, min=1, max=6, spot taint. (Req 8.1)
- [ ] 2.8 Output cluster OIDC issuer URL, cluster identity, kubelet identity. **Verify:** `az bicep build` succeeds.

## 3. Namespace and Network Policy Manifests (Req 3)

- [ ] 3.1 Create `DevOps/Azure/helm/platform/namespaces.yaml` defining 5 namespaces with resource quotas.
- [ ] 3.2 Create `DevOps/Azure/helm/platform/network-policies/` with default-deny per namespace.
- [ ] 3.3 Add allow policy: `fxops-agents` → `fxops-services:8080`. (Req 3.4)
- [ ] 3.4 Add allow policy: `fxops-sidecars` → `fxops-agents:5678`.
- [ ] 3.5 Add allow policy: `fxops-services` → data subnet Private Endpoints on required ports.
- [ ] 3.6 Add allow policy: `fxops-portals` → `fxops-services:8080`.
- [ ] 3.7 Add resource quotas (services: 32 CPU/64Gi; agents: 16 CPU/64Gi). **Verify:** `kubectl apply --dry-run=client` passes.

## 4. Workload Identity Module (Req 4)

- [ ] 4.1 Create User-Assigned Managed Identity for each of 7 services.
- [ ] 4.2 Create Federated Credential linking AKS OIDC issuer + namespace + SA to each identity.
- [ ] 4.3 Assign Azure RBAC roles per §6 table (Key Vault, Event Hub, etc.).
- [ ] 4.4 Output identity client IDs for Helm service account annotations. **Verify:** `az bicep build` shows 7 identities.

## 5. Helm Library Chart (Req 5)

- [ ] 5.1 Create `DevOps/Azure/helm/charts/fxops-base/Chart.yaml` (type: library).
- [ ] 5.2 Create `_deployment.tpl` with container spec, resource requests/limits, probes, env vars.
- [ ] 5.3 Create `_service.tpl` with ClusterIP service template.
- [ ] 5.4 Create `_hpa.tpl` with CPU scaling, min=2, max=configurable, stabilization=300s. (Req 7)
- [ ] 5.5 Create `_serviceaccount.tpl` with Workload Identity annotations (client ID, tenant ID).
- [ ] 5.6 Create `_configmap.tpl` for externalized configuration. **Verify:** `helm lint charts/fxops-base` passes.

## 6. Service Helm Charts (Req 5)

- [ ] 6.1 Create `DevOps/Azure/helm/services/trade-ingest/Chart.yaml` depending on `fxops-base`.
- [ ] 6.2 Create `values-dev.yaml` and `values-prod.yaml` with image tag, replicas, resource limits, Azure endpoints.
- [ ] 6.3 Repeat for remaining 6 services.
- [ ] 6.4 Create portal charts under `DevOps/Azure/helm/portals/`. **Verify:** `helm template` renders valid YAML.

## 7. Ingress — Application Gateway + AGIC (Req 6)

- [ ] 7.1 Define Application Gateway resource in Bicep with WAF v2 SKU.
- [ ] 7.2 Create Ingress resource YAML with AGIC annotations, Key Vault TLS cert reference.
- [ ] 7.3 Define routing rules for portals (`/admin`, `/traderdesk`, `/blotter`) and API (`/api/v1/*`).
- [ ] 7.4 Verify no data-store port exposed via ingress (no 5432/9093/6380/10255). **Verify:** ingress YAML inspection.

## 8. Tagging and Cost Controls (Req 8)

- [ ] 8.1 Add tags (`project`, `environment`, `managed-by`) to all Bicep resources.
- [ ] 8.2 Configure dev parameters with min node counts of 1 per user pool.
- [ ] 8.3 Enable AKS diagnostic settings with 90-day Log Analytics retention. **Verify:** tags in `az bicep build` output.
