# Tasks — DocumentDB (Document Store on AWS)

> **Stage 3 of 3** (`requirements.md → design.md → tasks.md`). Execute top-to-bottom;
> each task is atomic and independently verifiable. Mark `[x]` as completed.

## 0. Scaffold

- [ ] 0.1 Create `DevOps/AWS/terraform/modules/documentdb/` with `main.tf`, `security.tf`, `secrets.tf`, `monitoring.tf`, `variables.tf`, `outputs.tf`. (§8)
- [ ] 0.2 Add module reference in `DevOps/AWS/terraform/environments/dev/main.tf` and `prod/main.tf`.
- [ ] 0.3 Create `DevOps/AWS/scripts/docdb-indexes.js` for index provisioning.

## 1. Cluster Resource (Req 1)

- [ ] 1.1 Define `aws_docdb_cluster` with engine `docdb`, version `7.0.0`, identifier `fxops-docdb-<env>`. (§2)
- [ ] 1.2 Set `storage_encrypted = true` with KMS CMK ARN. (Req 3.2)
- [ ] 1.3 Set `backup_retention_period = 7` for prod, `1` for dev. (Req 6.1)
- [ ] 1.4 Enable CloudWatch log exports: audit, profiler. (Req 3.5)
- [ ] 1.5 Set `deletion_protection = true` for prod. **Verify:** `terraform validate` passes.

## 2. Cluster Instances (Req 1)

- [ ] 2.1 Define primary instance: `db.r6g.large` (prod) / `db.t4g.medium` (dev). (Req 7.1, 7.2)
- [ ] 2.2 Define 1 replica instance for prod, 0 for dev. (Req 1.1, 1.2)
- [ ] 2.3 Place instances in data subnet group across AZs. (Req 1.5) **Verify:** `terraform plan` shows correct instance count.

## 3. Parameter Group (Req 3)

- [ ] 3.1 Define `aws_docdb_cluster_parameter_group` with family `docdb7.0`. (§3)
- [ ] 3.2 Set `tls = enabled` (enforce TLS). (Req 3.1)
- [ ] 3.3 Set `audit_logs = enabled`. (Req 3.5)
- [ ] 3.4 Set `profiler = enabled`, `profiler_threshold_ms = 100`. **Verify:** parameter group referenced by cluster.

## 4. Security Group (Req 3)

- [ ] 4.1 Define security group allowing ingress on port 27017 from EKS services SG only. (§4)
- [ ] 4.2 No public ingress rules. (Req 3.4) **Verify:** no `0.0.0.0/0` ingress in `terraform plan`.

## 5. Secrets Manager (Req 3.3)

- [ ] 5.1 Define `aws_secretsmanager_secret` for DocumentDB master password.
- [ ] 5.2 Generate random password and store as secret version.
- [ ] 5.3 Configure rotation schedule (90 days). **Verify:** secret created in plan.

## 6. Index Provisioning (Req 4)

- [ ] 6.1 Author `DevOps/AWS/scripts/docdb-indexes.js` with all required indexes. (§6)
- [ ] 6.2 Include compound index `{tradeId: 1, occurredAt: -1}` for audit history. (Req 4.3)
- [ ] 6.3 Include TTL index on `operational_context.createdAt` (86400s). (Req 4.4)
- [ ] 6.4 Include correlation ID index for observability queries.
- [ ] 6.5 Create Kubernetes Job manifest to run index script post-deploy. (Req 4.2)
- [ ] 6.6 Verify idempotency: script uses `createIndex` (no-op on existing). **Verify:** script runs without error locally.

## 7. Service Helm Values (Req 2)

- [ ] 7.1 Add AWS profile connection string to trade-lifecycle Helm values. (§5)
- [ ] 7.2 Add `retryWrites=false` and `tls=true` parameters. (Req 2.4, 2.3)
- [ ] 7.3 Add `readPreference=secondaryPreferred` for read routing. (Req 2.2)
- [ ] 7.4 Mount `rds-combined-ca-bundle.pem` as a ConfigMap/volume. (Req 2.3)
- [ ] 7.5 Update state-reconciliation service Helm values similarly. **Verify:** `helm template` renders valid URI.

## 8. Monitoring (Req 5)

- [ ] 8.1 Define CloudWatch alarm: `CPUUtilization > 80%`. (§7)
- [ ] 8.2 Define CloudWatch alarm: `FreeableMemory < 1GB`. (§7)
- [ ] 8.3 Define CloudWatch alarm: `ReplicaLag > 5s`. (§7) **Verify:** alarms present in `terraform plan`.

## 9. Outputs

- [ ] 9.1 Output `cluster_endpoint` (write) and `reader_endpoint` (read).
- [ ] 9.2 Output port (27017) and security group ID.
- [ ] 9.3 Output `master_secret_arn` for External Secrets Operator. **Verify:** `terraform output` shows expected values.
