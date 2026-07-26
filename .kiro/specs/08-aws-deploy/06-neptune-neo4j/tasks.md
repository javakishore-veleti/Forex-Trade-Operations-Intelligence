# Tasks — Neptune / Neo4j (Graph Store on AWS)

> **Stage 3 of 3** (`requirements.md → design.md → tasks.md`). Execute top-to-bottom;
> each task is atomic and independently verifiable. Mark `[x]` as completed.

## 0. Scaffold

- [x] 0.1 Create `DevOps/AWS/terraform/modules/neptune/` with `main.tf`, `security.tf`, `params.tf`, `monitoring.tf`, `variables.tf`, `outputs.tf`. (§9)
- [x] 0.2 Add module reference in `DevOps/AWS/terraform/environments/dev/main.tf` and `prod/main.tf`.
- [x] 0.3 Create `DevOps/AWS/scripts/` for migration scripts.
- [x] 0.4 Create `docs/adr/0008-graph-store-aws.md` ADR placeholder. (Req 1.3)

## 1. ADR — Neptune vs Neo4j on EKS (Req 1.3)

- [x] 1.1 Write ADR documenting decision to use Neptune as primary.
- [x] 1.2 Include trade-off table: managed ops vs query compatibility vs cost.
- [x] 1.3 Document fallback path (Neo4j Helm chart on EKS) if openCypher gaps are blocking. (Req 1.2)
- [x] 1.4 Include cost comparison: Neptune provisioned vs Neo4j on r7g instances. (Req 6.1) **Verify:** ADR committed.

## 2. Neptune Cluster (Req 1, 4)

- [x] 2.1 Define `aws_neptune_cluster` with identifier `fxops-graph-<env>`, engine `neptune`. (§2)
- [x] 2.2 Set `storage_encrypted = true` with KMS CMK. (Req 4.2)
- [x] 2.3 Enable IAM database authentication. (Req 4.3)
- [x] 2.4 Enable audit log CloudWatch exports. (Req 4.5)
- [x] 2.5 Set backup retention: 7 days (prod), 1 day (dev). (Req 6)
- [x] 2.6 Set deletion protection for prod. **Verify:** `terraform validate` passes.

## 3. Neptune Instances (Req 1.4)

- [x] 3.1 Define primary instance: `db.r6g.large` (prod) / `db.t4g.medium` (dev). (Req 6.2)
- [x] 3.2 Define 1 read replica for prod, 0 for dev. (Req 1.4)
- [x] 3.3 Place in data subnet group. (Req 1.5) **Verify:** `terraform plan` shows correct instances.

## 4. Parameter Group (Req 5)

- [x] 4.1 Define `aws_neptune_cluster_parameter_group` with family `neptune1.3`. (§3)
- [x] 4.2 Set `neptune_enable_audit_log = 1`. (Req 4.5)
- [x] 4.3 Set `neptune_query_timeout = 30000` (30s). (Req 5.1) **Verify:** parameter group referenced by cluster.

## 5. Security Group (Req 4)

- [x] 5.1 Define security group allowing ingress on port 8182 from EKS services SG only. (§4)
- [x] 5.2 No public ingress. (Req 4.4) **Verify:** no `0.0.0.0/0` ingress.

## 6. Query Compatibility Documentation (Req 2)

- [x] 6.1 Create `docs/neptune-openCypher-compat.md` listing supported/unsupported features. (§5)
- [x] 6.2 Identify APOC procedure replacements for contagion-analysis agent. (Req 2.2)
- [x] 6.3 Verify contagion query works against Neptune by testing syntax. (§5)
- [x] 6.4 Document any query rewriting needed. **Verify:** doc committed.

## 7. Data Migration (Req 3)

- [x] 7.1 Create `DevOps/AWS/scripts/neptune-export.sh` — Cypher export from local Neo4j to CSV. (§6, Req 3.1)
- [x] 7.2 Create `DevOps/AWS/scripts/neo4j_to_neptune.py` — CSV transform to Neptune bulk loader format. (§6, Req 3.3)
- [x] 7.3 Document S3 upload + Neptune bulk loader invocation. (§6)
- [x] 7.4 Create Kubernetes Job manifest for migration execution.
- [x] 7.5 Test with synthetic `FX-` data (node: Trade, Book, Counterparty, Region). (Req 3.4) **Verify:** migration script runs end-to-end locally.

## 8. Self-Managed Neo4j Fallback (Req 1.2)

- [x] 8.1 Create `DevOps/AWS/helm/platform/neo4j/Chart.yaml` using official Neo4j Helm chart as dependency.
- [x] 8.2 Create `values-dev.yaml` and `values-prod.yaml` with resource limits, TLS, auth. (§7)
- [x] 8.3 Define PVC with gp3 StorageClass for graph data persistence. **Verify:** `helm template` renders valid YAML.

## 9. Service Helm Integration

- [x] 9.1 Output `cluster_endpoint` and `reader_endpoint` from Neptune module.
- [x] 9.2 Update state-reconciliation service Helm values with Neptune connection config. (§8)
- [x] 9.3 Add Neptune IAM auth plugin dependency note in service POM. **Verify:** `terraform output` shows endpoints.
