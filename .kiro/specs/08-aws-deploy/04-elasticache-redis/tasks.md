# Tasks — ElastiCache Redis (Cache on AWS)

> **Stage 3 of 3** (`requirements.md → design.md → tasks.md`). Execute top-to-bottom;
> each task is atomic and independently verifiable. Mark `[x]` as completed.

## 0. Scaffold

- [ ] 0.1 Create `DevOps/AWS/terraform/modules/elasticache/` with `main.tf`, `security.tf`, `secrets.tf`, `monitoring.tf`, `variables.tf`, `outputs.tf`. (§9)
- [ ] 0.2 Add module reference in `DevOps/AWS/terraform/environments/dev/main.tf` and `prod/main.tf`.

## 1. Replication Group (Req 1)

- [ ] 1.1 Define `aws_elasticache_replication_group` with cluster mode enabled, engine `redis`, version `7.1`. (§2)
- [ ] 1.2 Set `num_node_groups = 2` for prod, `1` for dev. (Req 1.1, Req 6.2)
- [ ] 1.3 Set `replicas_per_node_group = 1` for prod, `0` for dev. (Req 1.2)
- [ ] 1.4 Set node type: `cache.r7g.large` prod, `cache.t4g.micro` dev. (Req 6.1)
- [ ] 1.5 Enable `automatic_failover` and `multi_az` for prod. (Req 1.3)
- [ ] 1.6 Set `transit_encryption_enabled = true`, `at_rest_encryption_enabled = true`. (Req 3.1, 3.2)
- [ ] 1.7 Set `auth_token` from Secrets Manager variable. (Req 3.3)
- [ ] 1.8 Place in data subnet group. (Req 1.5) **Verify:** `terraform validate` passes.

## 2. Parameter Group (Req 2)

- [ ] 2.1 Define `aws_elasticache_parameter_group` with family `redis7`. (§3)
- [ ] 2.2 Set `maxmemory-policy = allkeys-lru`. (Req 2.1)
- [ ] 2.3 Set `tcp-keepalive = 300`, `timeout = 0`. (§3)
- [ ] 2.4 Set `notify-keyspace-events = Ex` for TTL monitoring. **Verify:** parameter group referenced by replication group.

## 3. Security Group (Req 3)

- [ ] 3.1 Define security group allowing ingress on port 6379 from EKS services SG only. (§4)
- [ ] 3.2 No `0.0.0.0/0` ingress. (Req 3.4) **Verify:** no public ingress in `terraform plan`.

## 4. Secrets Manager (Req 3.3, 3.5)

- [ ] 4.1 Define `aws_secretsmanager_secret` for Redis auth token. (§5)
- [ ] 4.2 Generate 64-char random password (no special chars for Redis compatibility). (§5)
- [ ] 4.3 Store as secret version. **Verify:** secret created in `terraform plan`.

## 5. Subnet Group (Req 1.5)

- [ ] 5.1 Define `aws_elasticache_subnet_group` referencing data subnet IDs from VPC module.
- [ ] 5.2 Tag with `project: fxops`. **Verify:** subnet group referenced by replication group.

## 6. Service Helm Values (Req 4)

- [ ] 6.1 Update trade-ingest Helm `values-dev.yaml` / `values-prod.yaml` with Redis cluster endpoint, TLS config. (§6)
- [ ] 6.2 Update risk-calculation, business-calendar, state-reconciliation, trade-lifecycle Helm values similarly.
- [ ] 6.3 Configure Lettuce pool: `max-active=16`, `max-idle=8`, `min-idle=2`. (Req 4.4)
- [ ] 6.4 Set `timeout=2000ms`, `connect-timeout=2000ms`. (Req 4.5) **Verify:** `helm template` renders valid Redis config.

## 7. Monitoring (Req 5)

- [ ] 7.1 Define CloudWatch alarm: `EngineCPUUtilization > 80%`. (§8)
- [ ] 7.2 Define CloudWatch alarm: `DatabaseMemoryUsagePercentage > 80%`. (Req 5.4)
- [ ] 7.3 Define CloudWatch alarm: `Evictions > 100/min`. (Req 5.4)
- [ ] 7.4 Define CloudWatch alarm: `ReplicationLag > 1s`. (Req 5.4) **Verify:** alarms present in `terraform plan`.

## 8. Outputs

- [ ] 8.1 Output `configuration_endpoint` for Helm chart injection.
- [ ] 8.2 Output `auth_token_secret_arn` for External Secrets Operator.
- [ ] 8.3 Output security group ID for cross-module references. **Verify:** `terraform output` shows expected values.
