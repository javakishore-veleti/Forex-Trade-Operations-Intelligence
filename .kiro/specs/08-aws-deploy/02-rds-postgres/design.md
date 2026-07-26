# Design Document — RDS PostgreSQL (Relational Store on AWS)

> **Stage 2 of 3** (`requirements.md → design.md → tasks.md`). Resolves `CloudTargetBinding`
> for `RELATIONAL_STORE` → AWS RDS PostgreSQL 16.x. Concrete Terraform configuration.

## 1. Overview

The platform `RELATIONAL_STORE` maps to **Amazon RDS for PostgreSQL 16.x** with RDS Proxy for
connection pooling. All services connect via RDS Proxy; direct DB access is blocked by security groups.

**Technology Bindings:**

| Technology Role | AWS Service | Configuration |
|---|---|---|
| `RELATIONAL_STORE` | Amazon RDS for PostgreSQL | 16.x, Multi-AZ |
| Connection Pool | RDS Proxy | PostgreSQL engine family |
| Encryption at rest | AWS KMS | Customer-managed CMK |
| Credential management | AWS Secrets Manager | Auto-rotation 90 days |
| Schema migration | Flyway (in-service) | Runs in init container |

## 2. Instance Configuration

```hcl
# DevOps/AWS/terraform/modules/rds/main.tf (conceptual)
resource "aws_db_instance" "fxops" {
  identifier     = "fxops-${var.environment}"
  engine         = "postgres"
  engine_version = "16.4"
  instance_class = var.environment == "prod" ? "db.r7g.xlarge" : "db.t4g.medium"

  multi_az               = var.environment == "prod" ? true : false
  allocated_storage      = 100
  max_allocated_storage  = 500       # auto-scaling cap
  storage_type           = var.environment == "prod" ? "io2" : "gp3"
  iops                   = var.environment == "prod" ? 3000 : null
  storage_encrypted      = true
  kms_key_id             = var.kms_key_arn

  db_subnet_group_name   = aws_db_subnet_group.data.name
  vpc_security_group_ids = [aws_security_group.rds.id]
  publicly_accessible    = false

  db_name  = "fxops"
  username = "fxops_admin"
  manage_master_user_password = true  # Secrets Manager managed

  parameter_group_name = aws_db_parameter_group.fxops.name
  backup_retention_period = 30
  backup_window           = "03:00-04:00"
  maintenance_window      = "sun:04:30-sun:05:30"
  copy_tags_to_snapshot   = true

  enabled_cloudwatch_logs_exports = ["postgresql", "upgrade"]
  performance_insights_enabled    = true
  deletion_protection             = var.environment == "prod" ? true : false
}
```

## 3. Parameter Group

```hcl
resource "aws_db_parameter_group" "fxops" {
  name   = "fxops-pg16-${var.environment}"
  family = "postgres16"

  parameter { name = "shared_buffers"               value = "{DBInstanceClassMemory/4}" }
  parameter { name = "effective_cache_size"          value = "{DBInstanceClassMemory*3/4}" }
  parameter { name = "work_mem"                     value = "65536" }       # 64MB
  parameter { name = "checkpoint_completion_target"  value = "0.9" }
  parameter { name = "max_connections"              value = "200" }
  parameter { name = "log_min_duration_statement"   value = "500" }         # slow query > 500ms
  parameter { name = "log_statement"                value = "ddl" }
  parameter { name = "ssl"                          value = "1" apply_method = "pending-reboot" }
  parameter { name = "rds.force_ssl"                value = "1" }
}
```

## 4. Security Groups

```hcl
resource "aws_security_group" "rds" {
  name_prefix = "fxops-rds-"
  vpc_id      = var.vpc_id

  ingress {
    from_port       = 5432
    to_port         = 5432
    protocol        = "tcp"
    security_groups = [var.rds_proxy_sg_id]   # Only RDS Proxy can connect
  }
  egress { from_port = 0; to_port = 0; protocol = "-1"; cidr_blocks = ["0.0.0.0/0"] }
}

resource "aws_security_group" "rds_proxy" {
  name_prefix = "fxops-rds-proxy-"
  vpc_id      = var.vpc_id

  ingress {
    from_port       = 5432
    to_port         = 5432
    protocol        = "tcp"
    security_groups = [var.eks_services_sg_id]  # Only EKS service pods
  }
}
```

## 5. RDS Proxy (Connection Pooling)

```hcl
resource "aws_db_proxy" "fxops" {
  name                   = "fxops-proxy-${var.environment}"
  engine_family          = "POSTGRESQL"
  role_arn               = aws_iam_role.rds_proxy.arn
  vpc_subnet_ids         = var.data_subnet_ids
  vpc_security_group_ids = [aws_security_group.rds_proxy.id]
  require_tls            = true

  auth {
    auth_scheme = "SECRETS"
    secret_arn  = aws_secretsmanager_secret.rds_credentials.arn
    iam_auth    = "REQUIRED"
  }
}

resource "aws_db_proxy_default_target_group" "fxops" {
  db_proxy_name = aws_db_proxy.fxops.name
  connection_pool_config {
    max_connections_percent      = 80
    max_idle_connections_percent = 50
    connection_borrow_timeout    = 120
  }
}
```

## 6. Connection String Pattern for Services

```yaml
# Helm values (per-service)
spring:
  datasource:
    url: jdbc:postgresql://${RDS_PROXY_ENDPOINT}:5432/fxops?sslmode=require&sslrootcert=/etc/ssl/rds-ca.pem
    username: ${SERVICE_DB_USER}  # IAM auth token generated at runtime
    hikari:
      maximum-pool-size: 10      # per-pod; RDS Proxy multiplexes upstream
      connection-timeout: 5000
      idle-timeout: 300000
```

## 7. Flyway Migration Strategy

- Each Middleware service owns a schema or table prefix within the `fxops` database.
- Migrations run in a Kubernetes **init container** before the main app starts.
- Directory structure: `Middleware/<service>/src/main/resources/db/migration/V{NNN}__{description}.sql`
- Flyway configured with `baselineOnMigrate=true` for initial deployment.
- Pre/post-migration manual snapshots automated via a pre-deploy Lambda or CI step.

## 8. Backup and DR

- Automated snapshots: daily at 03:00 UTC, retained 30 days.
- PITR: enabled, 5-minute granularity (WAL archiving).
- Cross-region copy: `aws_db_instance_automated_backups_replication` to DR region.
- Restore runbook documented in `docs/runbooks/rds-restore.md`.

## 9. Terraform Module Layout

```
DevOps/AWS/terraform/modules/rds/
├── main.tf          # RDS instance, subnet group, parameter group
├── proxy.tf         # RDS Proxy, target group
├── security.tf      # Security groups
├── secrets.tf       # Secrets Manager secret + rotation
├── variables.tf
└── outputs.tf       # proxy_endpoint, instance_endpoint, security_group_ids
```
