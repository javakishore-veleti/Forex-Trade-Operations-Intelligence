# Tasks — OpenSearch (Observability Logging on AWS)

> **Stage 3 of 3** (`requirements.md → design.md → tasks.md`). Execute top-to-bottom;
> each task is atomic and independently verifiable. Mark `[x]` as completed.

## 0. Scaffold

- [x] 0.1 Create `DevOps/AWS/terraform/modules/opensearch/` with `main.tf`, `security.tf`, `ism.tf`, `monitoring.tf`, `variables.tf`, `outputs.tf`. (§9)
- [x] 0.2 Add module reference in `DevOps/AWS/terraform/environments/dev/main.tf` and `prod/main.tf`.
- [x] 0.3 Create `DevOps/AWS/helm/platform/fluent-bit/` for log shipper.
- [x] 0.4 Create `DevOps/AWS/scripts/opensearch/` for index templates and saved searches.

## 1. OpenSearch Domain (Req 1)

- [x] 1.1 Define `aws_opensearch_domain` with engine version `OpenSearch_2.11`, name `fxops-logs-<env>`. (§2)
- [x] 1.2 Set cluster config: 2 data nodes (prod) / 1 (dev), r6g.large (prod) / t3.medium (dev). (Req 1.1, Req 7.2)
- [x] 1.3 Configure 3 dedicated master nodes for prod. (Req 1.2)
- [x] 1.4 Enable zone awareness for prod (2 AZs). (Req 1.1)
- [x] 1.5 Enable UltraWarm: 2 nodes for prod. (Req 7.1)
- [x] 1.6 Configure EBS: gp3, 500 GB (prod) / 50 GB (dev), provisioned IOPS for prod. (Req 1.4)
- [x] 1.7 Enable `encrypt_at_rest`, `node_to_node_encryption`, `enforce_https`. (Req 5.1, 5.2)
- [x] 1.8 Place in VPC with data subnet(s). (Req 1.5)
- [x] 1.9 Enable fine-grained access control with internal user database. (Req 5.3) **Verify:** `terraform validate` passes.

## 2. Security Group (Req 5)

- [x] 2.1 Define security group allowing HTTPS (443) from EKS DaemonSet SG and operator SG. (§3)
- [x] 2.2 No public access — VPC-only endpoint. (Req 5.4) **Verify:** no `0.0.0.0/0` ingress.

## 3. Fluent Bit DaemonSet (Req 2)

- [x] 3.1 Create Helm chart `DevOps/AWS/helm/platform/fluent-bit/Chart.yaml`. (§4)
- [x] 3.2 Configure input: tail `/var/log/containers/fxops-*.log`. (Req 2.1)
- [x] 3.3 Configure Kubernetes filter with `Merge_Log On`. (Req 2.2)
- [x] 3.4 Configure OpenSearch output with TLS, retry, buffer. (Req 2.4)
- [x] 3.5 Set index pattern `fxops-logs-%Y.%m.%d`. (§4)
- [x] 3.6 Configure IRSA service account for OpenSearch write access. **Verify:** `helm template` renders valid DaemonSet.

## 4. Index Templates (Req 2)

- [x] 4.1 Create `DevOps/AWS/scripts/opensearch/index-template.json` with field mappings. (§6)
- [x] 4.2 Map fields: timestamp, traceId, spanId, correlationId, tradeId, service, level, message. (Req 2.2)
- [x] 4.3 Set 2 shards, 1 replica, best_compression codec. (§6)
- [x] 4.4 Create `DevOps/AWS/scripts/opensearch/apply-templates.sh` to PUT template via curl. **Verify:** JSON validates with `jq .`.

## 5. Index Lifecycle (ISM) Policy (Req 4)

- [x] 5.1 Create `DevOps/AWS/scripts/opensearch/ism-policy-logs.json` with hot→warm→delete. (§5)
- [x] 5.2 Set rollover: 50 GB or 7 days. (Req 4.2)
- [x] 5.3 Set warm transition at 7 days (read-only + UltraWarm migration). (Req 4.1)
- [x] 5.4 Set delete at 30 days. (Req 4.1)
- [x] 5.5 Create separate ISM policy for DLQ/audit indexes: 90-day retention. (Req 4.4)
- [x] 5.6 Create `apply-ism.sh` script. **Verify:** JSON validates.

## 6. Saved Queries Migration (Req 3)

- [x] 6.1 Create `DevOps/AWS/scripts/opensearch/saved-searches.ndjson` with 4 saved searches. (§7, Req 3.1)
- [x] 6.2 Define index patterns for each service log stream. (Req 3.2)
- [x] 6.3 Create `migrate-kibana-to-opensearch.py` script for NDJSON conversion. (Req 3.4)
- [x] 6.4 Create `apply-saved-objects.sh` to import via OpenSearch Dashboards API. **Verify:** script parses NDJSON.

## 7. Secrets Manager

- [x] 7.1 Define `aws_secretsmanager_secret` for OpenSearch admin password.
- [x] 7.2 Generate random password and store as secret version.
- [x] 7.3 Create Fluent Bit credentials (or use IRSA + FGAC role mapping). **Verify:** secret in `terraform plan`.

## 8. Monitoring (Req 6)

- [x] 8.1 Define CloudWatch alarm: `ClusterStatus.red` → P1 alert. (§8)
- [x] 8.2 Define CloudWatch alarm: `FreeStorageSpace < 20%`. (§8)
- [x] 8.3 Define CloudWatch alarm: `JVMMemoryPressure > 80%`. (§8)
- [x] 8.4 Define CloudWatch alarm: `SearchLatency p95 > 5000ms`. (Req 6.2) **Verify:** alarms in `terraform plan`.

## 9. Outputs

- [x] 9.1 Output `domain_endpoint` for Fluent Bit configuration.
- [x] 9.2 Output `dashboards_endpoint` for operator access (via VPN/bastion).
- [x] 9.3 Output security group ID and domain ARN. **Verify:** `terraform output` shows expected values.
