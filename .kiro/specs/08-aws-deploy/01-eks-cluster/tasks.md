# Tasks — EKS Cluster (Container Orchestration on AWS)

> **Stage 3 of 3** (`requirements.md → design.md → tasks.md`). Execute top-to-bottom;
> each task is atomic and independently verifiable. Mark `[x]` as completed.

## 0. Scaffold

- [x] 0.1 Create `DevOps/AWS/terraform/modules/vpc/` with `main.tf`, `variables.tf`, `outputs.tf`. (§2, Req 1)
- [x] 0.2 Create `DevOps/AWS/terraform/modules/eks/` with `main.tf`, `variables.tf`, `outputs.tf`. (§3, Req 1)
- [x] 0.3 Create `DevOps/AWS/terraform/modules/irsa/` with `main.tf`, `variables.tf`, `outputs.tf`. (§6, Req 4)
- [x] 0.4 Create `DevOps/AWS/terraform/modules/alb-controller/` with `main.tf`. (§8, Req 6)
- [x] 0.5 Create `DevOps/AWS/terraform/environments/dev/` with `main.tf`, `variables.tf`, `terraform.tfvars`. (§11)
- [x] 0.6 Create `DevOps/AWS/terraform/environments/prod/` with same structure. (§11)
- [x] 0.7 Create `DevOps/AWS/terraform/backend.tf` with S3 state backend placeholder. (§11)

## 1. VPC Module (Req 1)

- [x] 1.1 Define VPC resource with CIDR `10.0.0.0/16` (parameterized via variable). Enable DNS hostnames.
- [x] 1.2 Define 3 public subnets (one per AZ) for NAT GW and ALB.
- [x] 1.3 Define 3 private subnets (one per AZ) for EKS worker nodes.
- [x] 1.4 Define 3 data subnets (one per AZ) for managed data services (RDS, MSK, etc.).
- [x] 1.5 Create NAT Gateways (one per AZ for HA) with Elastic IPs.
- [x] 1.6 Create route tables: public → IGW, private → NAT GW, data → NAT GW.
- [x] 1.7 Tag all subnets with `kubernetes.io/cluster/fxops-<env>` for EKS discovery.
- [x] 1.8 Output VPC ID, subnet IDs, and CIDR blocks for downstream modules. **Verify:** `terraform validate` passes.

## 2. EKS Cluster Module (Req 1, 2)

- [x] 2.1 Define EKS cluster resource with version `1.30`, private endpoint enabled, public endpoint disabled.
- [x] 2.2 Configure envelope encryption for secrets using a KMS key ARN variable.
- [x] 2.3 Enable control plane logging: api, audit, authenticator, controllerManager, scheduler. (Req 8.3)
- [x] 2.4 Define `services` node group: m7g.xlarge, min=3, max=10, private subnets only. (Req 2, §4)
- [x] 2.5 Define `agents` node group: r7g.large, min=1, max=4, private subnets. (Req 2, §4)
- [x] 2.6 Define `spot-sidecars` node group: m7g.large spot, min=1, max=6, taint `spot=true:PreferNoSchedule`. (Req 8.1)
- [x] 2.7 Configure VPC CNI addon with custom pod CIDR. (§2)
- [x] 2.8 Output cluster endpoint, OIDC provider ARN, cluster security group ID. **Verify:** `terraform plan` succeeds without errors.

## 3. Namespace and Network Policy Manifests (Req 3)

- [x] 3.1 Create `DevOps/AWS/helm/platform/namespaces.yaml` defining 5 namespaces with resource quotas.
- [x] 3.2 Create `DevOps/AWS/helm/platform/network-policies/` with deny-all default per namespace.
- [x] 3.3 Add allow policy: `fxops-agents` → `fxops-services:8080`. (Req 3.4)
- [x] 3.4 Add allow policy: `fxops-sidecars` → `fxops-agents:5678`. (§5)
- [x] 3.5 Add allow policy: `fxops-services` → data subnet CIDRs on required ports. (§5)
- [x] 3.6 Add resource quotas per namespace (e.g., services: 32 CPU, 64Gi; agents: 8 CPU, 32Gi). **Verify:** `kubectl apply --dry-run=client -f` passes.

## 4. IRSA Module (Req 4)

- [x] 4.1 Define IAM OIDC identity provider for the EKS cluster.
- [x] 4.2 Create IAM role `fxops-trade-ingest-<env>` with trust policy scoped to SA `trade-ingest` in `fxops-services`.
- [x] 4.3 Repeat for all 7 services with appropriately scoped permissions per §6 table.
- [x] 4.4 Output role ARNs for annotation in Helm service account templates. **Verify:** `terraform plan` shows 7 IAM roles.

## 5. Helm Library Chart (Req 5)

- [x] 5.1 Create `DevOps/AWS/helm/charts/fxops-base/Chart.yaml` (type: library).
- [x] 5.2 Create `_deployment.tpl` with container spec, resource requests/limits, probes, env vars.
- [x] 5.3 Create `_service.tpl` with ClusterIP service template.
- [x] 5.4 Create `_hpa.tpl` with CPU-based scaling, min=2, max=configurable, stabilization=300s. (Req 7)
- [x] 5.5 Create `_serviceaccount.tpl` with IRSA annotation placeholder.
- [x] 5.6 Create `_configmap.tpl` for externalized configuration. **Verify:** `helm lint charts/fxops-base` passes.

## 6. Service Helm Charts (Req 5)

- [x] 6.1 Create `DevOps/AWS/helm/services/trade-ingest/Chart.yaml` depending on `fxops-base`.
- [x] 6.2 Create `values-dev.yaml` and `values-prod.yaml` with image tag, replicas, resource limits, env vars.
- [x] 6.3 Repeat for remaining 6 services (trade-lifecycle, risk-calculation, eod-processing, business-calendar, state-reconciliation, event-sequence-processor).
- [x] 6.4 Create portal charts under `DevOps/AWS/helm/portals/` (admin, traderdesk, fxtradeblotter). **Verify:** `helm template` renders valid YAML for each chart.

## 7. Ingress and ALB Controller (Req 6)

- [x] 7.1 Define ALB controller installation in Terraform (Helm release or IRSA + manifest).
- [x] 7.2 Create ingress resource YAML with ACM cert ARN placeholder, TLS 1.3 policy. (§8)
- [x] 7.3 Define routing rules for portals and API services. (Req 6.4)
- [x] 7.4 Ensure no data-store endpoint exposed via ingress. (Req 6.5) **Verify:** ingress YAML contains no port 5432/9094/6379/27017/8182 backends.

## 8. Tagging and Cost Controls (Req 8)

- [x] 8.1 Add default tags module applying `project`, `environment`, `managed-by` to all resources.
- [x] 8.2 Define dev environment `terraform.tfvars` with min node counts of 1. (Req 8.4)
- [x] 8.3 Configure CloudWatch log retention to 90 days. (Req 8.3) **Verify:** tags present in `terraform plan` output.
