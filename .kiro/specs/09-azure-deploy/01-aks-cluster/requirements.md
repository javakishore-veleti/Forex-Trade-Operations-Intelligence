# Requirements Document — AKS Cluster (Container Orchestration on Azure)

> **Technology-agnostic spec.** References **Technology Roles** from `01-initial-setup/01-technology-stack`
> and resolves via `CloudTargetBinding` for Azure. Inherits `architecture-golden-path/01-service-nfrs`.

## Introduction

This spec defines the container orchestration platform requirements for deploying all `Middleware/`
services, portals, sidecars, and the agent platform to the Azure cloud target. It covers cluster
topology, node capacity, namespace isolation, managed identity, ingress routing, auto-scaling,
and Helm-based deployment packaging. All example identifiers use synthetic `FX-` prefixes.

---

## Glossary

- **ContainerOrchestrator**: The `CloudTargetBinding` for container orchestration on Azure (AKS).
- **NodePool**: A pool of compute VMs serving workloads within the orchestrator.
- **Namespace**: A logical isolation boundary within the orchestrator separating workload tiers.
- **ManagedIdentity**: The Azure AD workload identity assumed by a running service pod.
- **HelmRelease**: A versioned, parameterized deployment unit packaging a single service.
- **AGIC**: Application Gateway Ingress Controller — layer-7 reverse proxy routing external traffic.
- **HPA**: Horizontal Pod Autoscaler — scales replicas based on observed metrics.

---

## Requirements

### Requirement 1: Cluster Topology and Networking

**User Story:** As a platform engineer, I want the container orchestrator deployed in a private,
multi-zone topology, so that workloads are resilient to single-zone failure and unexposed to the internet.

#### Acceptance Criteria

1. THE cluster control plane SHALL use a private API server endpoint (no public IP).
2. ALL worker nodes SHALL run in private subnets with no direct internet-routable IP addresses.
3. THE cluster SHALL use Azure CNI networking with a dedicated VNet and non-overlapping CIDR ranges for pods and services.
4. EGRESS from pods SHALL route through Azure NAT Gateway; no public IP assignment to pods.
5. THE cluster SHALL enable Azure Disk encryption with a customer-managed key for secrets at rest.
6. THE cluster SHALL span at least 3 availability zones within the target region.

---

### Requirement 2: Node Pools and Capacity

**User Story:** As a capacity planner, I want node pools sized and segmented by workload class,
so that compute resources match workload profiles without over-provisioning.

#### Acceptance Criteria

1. THE cluster SHALL define at least two user node pools: one for `SERVICE_FRAMEWORK` workloads (compute-optimized) and one for `AGENT_PLATFORM` / `SIDECAR_LANGUAGE` workloads (memory-optimized).
2. EACH node pool SHALL use Azure VMSS with auto-scaling enabled (defined min/max node counts).
3. NODE pools SHALL use specific VM SKUs pinned to a defined family — no open instance selection.
4. THE platform SHALL support at least 7 `SERVICE_FRAMEWORK` pods + 3 portal pods + 4 sidecar pods + 1 agent platform pod concurrently at minimum capacity.
5. THE system node pool SHALL run cluster-critical system pods (CoreDNS, kube-proxy, AGIC).

---

### Requirement 3: Namespace Isolation

**User Story:** As a security engineer, I want workloads isolated by tier via namespaces, so that
blast radius is limited and RBAC policies are enforceable per tier.

#### Acceptance Criteria

1. THE cluster SHALL define namespaces: `fxops-services`, `fxops-portals`, `fxops-agents`, `fxops-sidecars`, `fxops-infra`.
2. NETWORK policies SHALL restrict inter-namespace traffic to declared dependencies only.
3. EACH namespace SHALL have resource quotas (CPU, memory) preventing a single namespace from starving others.
4. THE `fxops-agents` namespace SHALL NOT have direct network access to data stores; it communicates via `AGENT_TOOL_PROTOCOL` to services in `fxops-services`.

---

### Requirement 4: Managed Identity (Workload Identity)

**User Story:** As a security engineer, I want each service pod to assume a least-privilege managed
identity, so that credentials are never stored in config and access is auditable.

#### Acceptance Criteria

1. EVERY service pod SHALL assume a unique `ManagedIdentity` via Azure Workload Identity federation — no long-lived credentials in environment variables or secrets.
2. EACH `ManagedIdentity` SHALL grant access only to the specific Azure resources that service requires.
3. THE cluster admin identity SHALL be separate from service identities and restricted to platform engineers.
4. ALL identity bindings SHALL be auditable via Azure Activity Log and Entra ID sign-in logs.

---

### Requirement 5: Helm Chart Structure

**User Story:** As a developer, I want each Middleware service packaged as a Helm release with
shared base templates, so that deployments are repeatable and environment-parameterized.

#### Acceptance Criteria

1. EACH Middleware service SHALL have its own Helm chart inheriting from a shared library chart for common templates (deployment, service, configmap, HPA, service account).
2. EACH chart SHALL externalize all environment-specific values (image tag, replica count, resource limits, Azure resource endpoints) into a values file per environment.
3. HELM charts SHALL enforce resource requests and limits on every container — no unbounded pods.
4. THE shared library chart SHALL include readiness/liveness probe templates per GP-Rq-4.
5. ALL Helm chart image references SHALL use digest-pinned or semver-pinned tags — never `latest`.

---

### Requirement 6: Ingress (AGIC) and TLS Termination

**User Story:** As an operator, I want Application Gateway routing external HTTPS traffic to
portal and API services, so that TLS is terminated consistently and internal traffic is encrypted.

#### Acceptance Criteria

1. THE cluster SHALL deploy AGIC as the ingress controller integrated with Azure Application Gateway.
2. ALL external traffic SHALL terminate TLS at the Application Gateway using a certificate from Azure Key Vault.
3. INTERNAL service-to-service traffic SHALL use mTLS or service-mesh encryption — no plaintext between pods.
4. THE ingress SHALL route by hostname/path to portals and API services.
5. DATA store endpoints SHALL NOT be exposed via ingress — accessible only from within the VNet.

---

### Requirement 7: Horizontal Pod Autoscaling

**User Story:** As an operator, I want services to auto-scale based on load, so that throughput
scales with demand without manual intervention.

#### Acceptance Criteria

1. EVERY Middleware service deployment SHALL have an HPA policy with CPU-based scaling (target 70%) as default.
2. EVENT-STREAM consumer services SHALL additionally scale on consumer-lag metrics when available.
3. HPA min replicas SHALL be ≥ 2 for all services (high availability).
4. HPA max replicas SHALL be bounded to prevent runaway scaling and cost overruns.
5. SCALE-down stabilization SHALL be at least 300 seconds to prevent flapping.

---

### Requirement 8: Cost and Operational Considerations

**User Story:** As a FinOps stakeholder, I want cost controls and observability on cluster spend,
so that infrastructure costs are predictable and attributable.

#### Acceptance Criteria

1. NODE pools SHALL use Azure Spot VMs for non-critical workloads (sidecars, dev portals) where supported.
2. THE cluster SHALL tag all resources with `project:fxops`, `environment:<env>`, and `service:<name>` for cost attribution.
3. CLUSTER diagnostic logs and audit logs SHALL be enabled and retained for at least 90 days.
4. THE platform SHALL support a `dev` environment with reduced node counts (min 1 per pool) for cost savings.
