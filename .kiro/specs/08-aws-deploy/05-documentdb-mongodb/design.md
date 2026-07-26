# Design Document — DocumentDB (Document Store on AWS)

> **Stage 2 of 3** (`requirements.md → design.md → tasks.md`). Resolves `CloudTargetBinding`
> for `DOCUMENT_STORE` → AWS DocumentDB (MongoDB 7.0 compatible).

## 1. Overview

The platform `DOCUMENT_STORE` maps to **Amazon DocumentDB** with MongoDB 7.0 compatibility.
Services store audit histories, lifecycle event documents, and denormalized read models here.

**Technology Bindings:**

| Technology Role | AWS Service | Configuration |
|---|---|---|
| `DOCUMENT_STORE` | Amazon DocumentDB | MongoDB 7.0 compat, cluster mode |
| Encryption | KMS (at-rest) + TLS (in-transit) | Customer-managed CMK |
| Credentials | AWS Secrets Manager | Auto-rotation |
| Client | Spring Data MongoDB | Cluster-aware, TLS |

## 2. Cluster Configuration

```hcl
# DevOps/AWS/terraform/modules/documentdb/main.tf (conceptual)
resource "aws_docdb_cluster" "fxops" {
  cluster_identifier     = "fxops-docdb-${var.environment}"
  engine                 = "docdb"
  engine_version         = "7.0.0"
  master_username        = "fxops_admin"
  master_password        = var.docdb_master_password  # from Secrets Manager
  db_subnet_group_name   = aws_docdb_subnet_group.data.name
  vpc_security_group_ids = [aws_security_group.docdb.id]

  storage_encrypted = true
  kms_key_id        = var.kms_key_arn

  backup_retention_period = var.environment == "prod" ? 7 : 1
  preferred_backup_window = "03:00-04:00"

  enabled_cloudwatch_logs_exports = ["audit", "profiler"]
  deletion_protection             = var.environment == "prod" ? true : false

  db_cluster_parameter_group_name = aws_docdb_cluster_parameter_group.fxops.name
}

resource "aws_docdb_cluster_instance" "primary" {
  count              = 1
  identifier         = "fxops-docdb-${var.environment}-primary"
  cluster_identifier = aws_docdb_cluster.fxops.id
  instance_class     = var.environment == "prod" ? "db.r6g.large" : "db.t4g.medium"
}

resource "aws_docdb_cluster_instance" "replica" {
  count              = var.environment == "prod" ? 1 : 0
  identifier         = "fxops-docdb-${var.environment}-replica-${count.index}"
  cluster_identifier = aws_docdb_cluster.fxops.id
  instance_class     = var.environment == "prod" ? "db.r6g.large" : "db.t4g.medium"
}
```

## 3. Parameter Group

```hcl
resource "aws_docdb_cluster_parameter_group" "fxops" {
  family = "docdb7.0"
  name   = "fxops-docdb-params-${var.environment}"

  parameter { name = "tls"                  value = "enabled" }
  parameter { name = "audit_logs"           value = "enabled" }
  parameter { name = "profiler"             value = "enabled" }
  parameter { name = "profiler_threshold_ms" value = "100" }
}
```

## 4. Security Groups

```hcl
resource "aws_security_group" "docdb" {
  name_prefix = "fxops-docdb-"
  vpc_id      = var.vpc_id

  ingress {
    from_port       = 27017
    to_port         = 27017
    protocol        = "tcp"
    security_groups = [var.eks_services_sg_id]  # Only EKS service pods
  }

  egress { from_port = 0; to_port = 0; protocol = "-1"; cidr_blocks = ["0.0.0.0/0"] }
}
```

## 5. Connection String Pattern

```yaml
# Helm values — local profile
spring:
  data:
    mongodb:
      uri: mongodb://localhost:27017/fxops

# Helm values — AWS profile
spring:
  data:
    mongodb:
      uri: mongodb://${DOCDB_USER}:${DOCDB_PASSWORD}@${DOCDB_CLUSTER_ENDPOINT}:27017/fxops?tls=true&tlsCAFile=/etc/ssl/rds-combined-ca-bundle.pem&replicaSet=rs0&readPreference=secondaryPreferred&retryWrites=false
```

Key differences from local MongoDB:
- `retryWrites=false` — DocumentDB limitation
- `tls=true` + CA file required
- `replicaSet=rs0` required for replica set connection
- `readPreference=secondaryPreferred` routes reads to replicas

## 6. Index Provisioning Script

```javascript
// DevOps/AWS/scripts/docdb-indexes.js (run via mongosh or Kubernetes Job)
db = db.getSiblingDB("fxops");

// Trade audit history — used by trade-lifecycle-service
db.trade_audit_history.createIndex(
  { "tradeId": 1, "occurredAt": -1 },
  { name: "idx_trade_audit_timeline", background: true }
);

db.trade_audit_history.createIndex(
  { "correlationId": 1 },
  { name: "idx_audit_correlation", background: true }
);

// Lifecycle event documents — used by trade-lifecycle-service
db.lifecycle_events.createIndex(
  { "tradeId": 1, "eventType": 1, "timestamp": -1 },
  { name: "idx_lifecycle_trade_type", background: true }
);

// Operational context (TTL index) — expires after 24 hours
db.operational_context.createIndex(
  { "createdAt": 1 },
  { name: "idx_op_context_ttl", expireAfterSeconds: 86400, background: true }
);

print("✓ All DocumentDB indexes created/verified");
```

## 7. Monitoring

CloudWatch metrics to alarm on:
- `CPUUtilization` > 80%
- `FreeableMemory` < 1 GB
- `DatabaseConnections` > 80% of max
- `ReadIOPS` + `WriteIOPS` combined > provisioned threshold
- `ReplicaLag` > 5 seconds

## 8. Terraform Module Layout

```
DevOps/AWS/terraform/modules/documentdb/
├── main.tf          # Cluster, instances, subnet group, parameter group
├── security.tf      # Security group
├── secrets.tf       # Master password in Secrets Manager
├── monitoring.tf    # CloudWatch alarms
├── variables.tf
└── outputs.tf       # cluster_endpoint, reader_endpoint, port
```
