# Tasks — RDS PostgreSQL (Relational Store on AWS)

> **Stage 3 of 3** (`requirements.md → design.md → tasks.md`). Execute top-to-bottom;
> each task is atomic and independently verifiable. Mark `[x]` as completed.

## 0. Scaffold

- [ ] 0.1 Create `DevOps/AWS/terraform/modules/rds/` with `main.tf`, `proxy.tf`, `security.tf`, `secrets.tf`, `variables.tf`, `outputs.tf`. (§9)
- [ ] 0.2 Add module reference in `DevOps/AWS/terraform/environments/dev/main.tf` and `prod/main.tf`.

## 1. RDS Instance (Req 1, 5)

- [ ] 1.1 Define `aws_db_instance` resource with engine `postgres`, version `16.4`, identifier `fxops-<env>`. (§2)
- [ ] 1.2 Configure Multi-AZ for prod, single-AZ for dev via environment variable. (Req 1.1, Req 7.2)
- [ ] 1.3 Set storage: io2 + 3000 IOPS for prod, gp3 for dev; enable auto-scaling with 500 GB max. (Req 5.2, Req 7.4)
- [ ] 1.4 Enable storage encryption with KMS CMK ARN variable. (Req 3.3)
- [ ] 1.5 Set `publicly_accessible = false`, attach data subnet group. (Req 3.1)
- [ ] 1.6 Enable Performance Insights and CloudWatch log exports (postgresql, upgrade). (Req 5.3)
- [ ] 1.7 Set `backup_retention_period = 30`, `backup_window = "03:00-04:00"`. (Req 6.1)
- [ ] 1.8 Set `deletion_protection = true` for prod. **Verify:** `terraform validate` passes.

## 2. Parameter Group (Req 5)

- [ ] 2.1 Define `aws_db_parameter_group` with family `postgres16`. (§3)
- [ ] 2.2 Set parameters: `shared_buffers`, `effective_cache_size`, `work_mem=65536`, `max_connections=200`. (Req 5.1)
- [ ] 2.3 Set `log_min_duration_statement=500`, `rds.force_ssl=1`. (Req 5.3, Req 3.2)
- [ ] 2.4 Set `checkpoint_completion_target=0.9`. **Verify:** parameter group referenced by instance.

## 3. Security Groups (Req 3)

- [ ] 3.1 Define RDS security group allowing ingress on port 5432 from RDS Proxy SG only. (§4)
- [ ] 3.2 Define RDS Proxy security group allowing ingress on port 5432 from EKS services SG only. (§4)
- [ ] 3.3 Verify no security group rule allows `0.0.0.0/0` ingress. **Verify:** `terraform plan` shows no public ingress.

## 4. RDS Proxy (Req 2)

- [ ] 4.1 Define `aws_db_proxy` with engine family POSTGRESQL, `require_tls = true`. (§5)
- [ ] 4.2 Configure auth block with Secrets Manager ARN and `iam_auth = REQUIRED`. (Req 2.5, Req 3.4)
- [ ] 4.3 Define default target group with `max_connections_percent = 80`, `connection_borrow_timeout = 120`. (Req 2.2)
- [ ] 4.4 Register RDS instance as target. (§5)
- [ ] 4.5 Create IAM role for RDS Proxy with Secrets Manager read access. **Verify:** `terraform plan` shows proxy resource.

## 5. Secrets Manager (Req 3.4)

- [ ] 5.1 Define `aws_secretsmanager_secret` for RDS master credentials.
- [ ] 5.2 Configure automatic rotation with 90-day schedule. (Req 3.4)
- [ ] 5.3 Define per-service secrets (or IAM auth tokens) for application-level access.

## 6. Flyway Migration Setup (Req 4)

- [ ] 6.1 Document migration directory convention in `docs/runbooks/flyway-migration.md`.
- [ ] 6.2 Add init container template to Helm library chart `_deployment.tpl` running Flyway migrate. (§7)
- [ ] 6.3 Configure Flyway in Helm values: `url` → RDS Proxy endpoint, `baselineOnMigrate=true`.
- [ ] 6.4 Add pre-deploy snapshot step documentation (manual or CI-triggered). (Req 6.5)
- [ ] 6.5 Verify migration directory exists for each service that uses RDS. **Verify:** `flyway info` target can resolve.

## 7. Backup and DR (Req 6)

- [ ] 7.1 Enable PITR (automatic with backup retention > 0). (Req 6.2)
- [ ] 7.2 Configure cross-region backup replication resource. (Req 6.3)
- [ ] 7.3 Create `docs/runbooks/rds-restore.md` documenting PITR and snapshot restore procedures. (Req 6.4)
- [ ] 7.4 Add CI step placeholder for pre-migration manual snapshot. **Verify:** backup_retention_period = 30 in plan.

## 8. Outputs and Service Integration

- [ ] 8.1 Output RDS Proxy endpoint as `rds_proxy_endpoint` for Helm chart consumption.
- [ ] 8.2 Output security group IDs for cross-module references.
- [ ] 8.3 Update service Helm values to reference `rds_proxy_endpoint` placeholder. **Verify:** `terraform output` shows expected values.
