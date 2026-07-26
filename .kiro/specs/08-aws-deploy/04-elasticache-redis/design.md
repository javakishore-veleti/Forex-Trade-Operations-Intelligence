# Design Document — ElastiCache Redis (Cache on AWS)

> **Stage 2 of 3** (`requirements.md → design.md → tasks.md`). Resolves `CloudTargetBinding`
> for `CACHE` → AWS ElastiCache Redis 7.x. Concrete Terraform configuration.

## 1. Overview

The platform `CACHE` maps to **Amazon ElastiCache for Redis 7.x** in cluster mode with TLS,
AUTH token, and `allkeys-lru` eviction. Services connect via the cluster configuration endpoint.

**Technology Bindings:**

| Technology Role | AWS Service | Configuration |
|---|---|---|
| `CACHE` | Amazon ElastiCache for Redis | 7.1, cluster mode enabled |
| Encryption | In-transit TLS + at-rest KMS | AWS-managed CMK |
| Authentication | Redis AUTH token | Secrets Manager stored |
| Client | Spring Data Redis (Lettuce) | Cluster-aware, TLS |

## 2. Replication Group Configuration

```hcl
# DevOps/AWS/terraform/modules/elasticache/main.tf (conceptual)
resource "aws_elasticache_replication_group" "fxops" {
  replication_group_id = "fxops-cache-${var.environment}"
  description          = "FXOps Redis cache cluster"
  engine               = "redis"
  engine_version       = "7.1"
  node_type            = var.environment == "prod" ? "cache.r7g.large" : "cache.t4g.micro"
  port                 = 6379
  parameter_group_name = aws_elasticache_parameter_group.fxops.name

  # Cluster mode
  num_node_groups         = var.environment == "prod" ? 2 : 1
  replicas_per_node_group = var.environment == "prod" ? 1 : 0

  # Security
  at_rest_encryption_enabled = true
  transit_encryption_enabled = true
  auth_token                 = var.redis_auth_token  # from Secrets Manager
  kms_key_id                 = var.kms_key_arn

  # Networking
  subnet_group_name  = aws_elasticache_subnet_group.data.name
  security_group_ids = [aws_security_group.redis.id]

  # HA
  automatic_failover_enabled = var.environment == "prod" ? true : false
  multi_az_enabled           = var.environment == "prod" ? true : false

  # Maintenance
  maintenance_window     = "sun:05:00-sun:06:00"
  snapshot_retention_limit = var.environment == "prod" ? 7 : 1
  snapshot_window         = "03:00-04:00"

  apply_immediately = var.environment != "prod"
}
```

## 3. Parameter Group

```hcl
resource "aws_elasticache_parameter_group" "fxops" {
  name   = "fxops-redis7-${var.environment}"
  family = "redis7"

  parameter { name = "maxmemory-policy"     value = "allkeys-lru" }
  parameter { name = "tcp-keepalive"        value = "300" }
  parameter { name = "timeout"              value = "0" }
  parameter { name = "notify-keyspace-events" value = "Ex" }  # Expired events for TTL monitoring
}
```

## 4. Security Groups

```hcl
resource "aws_security_group" "redis" {
  name_prefix = "fxops-redis-"
  vpc_id      = var.vpc_id

  ingress {
    from_port       = 6379
    to_port         = 6379
    protocol        = "tcp"
    security_groups = [var.eks_services_sg_id]  # Only EKS service pods
  }

  egress { from_port = 0; to_port = 0; protocol = "-1"; cidr_blocks = ["0.0.0.0/0"] }
}
```

## 5. Secrets Manager for Auth Token

```hcl
resource "aws_secretsmanager_secret" "redis_auth" {
  name = "fxops/redis/auth-token-${var.environment}"
}

resource "aws_secretsmanager_secret_version" "redis_auth" {
  secret_id     = aws_secretsmanager_secret.redis_auth.id
  secret_string = random_password.redis_auth.result
}

resource "random_password" "redis_auth" {
  length  = 64
  special = false  # Redis AUTH token constraints
}
```

## 6. Connection Pattern for Spring Boot Services

```yaml
# Helm values (per-service)
spring:
  data:
    redis:
      cluster:
        nodes: ${ELASTICACHE_CONFIG_ENDPOINT}:6379
      password: ${REDIS_AUTH_TOKEN}
      ssl:
        enabled: true
      lettuce:
        pool:
          max-active: 16
          max-idle: 8
          min-idle: 2
        cluster:
          refresh:
            adaptive: true
            period: 30s
      timeout: 2000ms
      connect-timeout: 2000ms
```

## 7. Use Cases in Services

| Service | Cache Usage | Key Pattern | TTL |
|---|---|---|---|
| trade-ingest | Idempotency keys | `idemp:{tradeId}:{hash}` | 24h |
| risk-calculation | Rate limit / risk context | `risk:ctx:{tradeId}` | 1h |
| business-calendar | Calendar lookups | `cal:{region}:{date}` | 12h |
| state-reconciliation | Canonical state snapshot | `state:{tradeId}` | 30m |
| trade-lifecycle | Status cache | `lifecycle:{tradeId}` | 5m |

## 8. Monitoring

CloudWatch metrics to alarm on:
- `EngineCPUUtilization` > 80% → scale up notification
- `DatabaseMemoryUsagePercentage` > 80% → eviction pressure alert
- `Evictions` > 100/min → capacity alert
- `ReplicationLag` > 1s → failover readiness concern

## 9. Terraform Module Layout

```
DevOps/AWS/terraform/modules/elasticache/
├── main.tf          # Replication group, subnet group, parameter group
├── security.tf      # Security group
├── secrets.tf       # Auth token in Secrets Manager
├── monitoring.tf    # CloudWatch alarms
├── variables.tf
└── outputs.tf       # configuration_endpoint, auth_token_secret_arn
```
