# =============================================================================
# FXOps Platform — ElastiCache Redis 7.1 (Cluster Mode)
# =============================================================================

# --- Subnet Group ---
resource "aws_elasticache_subnet_group" "data" {
  name       = "fxops-redis-${var.environment}"
  subnet_ids = aws_subnet.data[*].id
  tags       = { Name = "fxops-redis-subnet-group" }
}

# --- Security Group ---
resource "aws_security_group" "redis" {
  name_prefix = "fxops-redis-"
  description = "ElastiCache Redis - only accessible from EKS services"
  vpc_id      = aws_vpc.main.id

  ingress {
    from_port       = 6379
    to_port         = 6379
    protocol        = "tcp"
    security_groups = [aws_security_group.eks_services.id]
  }

  egress {
    from_port   = 0
    to_port     = 0
    protocol    = "-1"
    cidr_blocks = ["0.0.0.0/0"]
  }

  tags = { Name = "fxops-redis-${var.environment}" }
}

# --- Parameter Group ---
resource "aws_elasticache_parameter_group" "fxops" {
  name   = "fxops-redis7-${var.environment}"
  family = "redis7"

  parameter {
    name  = "maxmemory-policy"
    value = "allkeys-lru"
  }
  parameter {
    name  = "tcp-keepalive"
    value = "300"
  }
  parameter {
    name  = "timeout"
    value = "0"
  }
  parameter {
    name  = "notify-keyspace-events"
    value = "Ex"
  }

  tags = { service = "elasticache" }
}

# --- Auth Token ---
resource "random_password" "redis_auth" {
  length  = 64
  special = false
}

# --- Replication Group (Cluster Mode) ---
resource "aws_elasticache_replication_group" "fxops" {
  replication_group_id = "fxops-cache-${var.environment}"
  description          = "FXOps Redis cache cluster - ${var.environment}"
  engine               = "redis"
  engine_version       = "7.1"
  node_type            = var.redis_node_type
  port                 = 6379
  parameter_group_name = aws_elasticache_parameter_group.fxops.name

  # Cluster mode sharding
  num_node_groups         = var.redis_num_node_groups
  replicas_per_node_group = var.redis_replicas_per_group

  # Security - TLS everywhere
  at_rest_encryption_enabled = true
  transit_encryption_enabled = true
  auth_token                 = random_password.redis_auth.result
  kms_key_id                 = aws_kms_key.fxops.arn

  # Networking
  subnet_group_name  = aws_elasticache_subnet_group.data.name
  security_group_ids = [aws_security_group.redis.id]

  # HA settings
  automatic_failover_enabled = var.redis_replicas_per_group > 0 ? true : false
  multi_az_enabled           = var.redis_replicas_per_group > 0 ? true : false

  # Maintenance
  maintenance_window       = "sun:05:00-sun:06:00"
  snapshot_retention_limit = var.environment == "prod" ? 7 : 1
  snapshot_window          = "03:00-04:00"

  apply_immediately = var.environment != "prod"

  tags = { service = "elasticache-redis" }
}

# --- CloudWatch Alarms ---
resource "aws_cloudwatch_metric_alarm" "redis_cpu" {
  alarm_name          = "fxops-redis-cpu-${var.environment}"
  comparison_operator = "GreaterThanThreshold"
  evaluation_periods  = 3
  metric_name         = "EngineCPUUtilization"
  namespace           = "AWS/ElastiCache"
  period              = 300
  statistic           = "Average"
  threshold           = 80
  alarm_description   = "Redis engine CPU > 80%"

  dimensions = { ReplicationGroupId = aws_elasticache_replication_group.fxops.id }
  tags       = { service = "elasticache" }
}

resource "aws_cloudwatch_metric_alarm" "redis_memory" {
  alarm_name          = "fxops-redis-memory-${var.environment}"
  comparison_operator = "GreaterThanThreshold"
  evaluation_periods  = 3
  metric_name         = "DatabaseMemoryUsagePercentage"
  namespace           = "AWS/ElastiCache"
  period              = 300
  statistic           = "Average"
  threshold           = 80
  alarm_description   = "Redis memory usage > 80%"

  dimensions = { ReplicationGroupId = aws_elasticache_replication_group.fxops.id }
  tags       = { service = "elasticache" }
}

resource "aws_cloudwatch_metric_alarm" "redis_evictions" {
  alarm_name          = "fxops-redis-evictions-${var.environment}"
  comparison_operator = "GreaterThanThreshold"
  evaluation_periods  = 2
  metric_name         = "Evictions"
  namespace           = "AWS/ElastiCache"
  period              = 60
  statistic           = "Sum"
  threshold           = 100
  alarm_description   = "Redis evictions > 100/min"

  dimensions = { ReplicationGroupId = aws_elasticache_replication_group.fxops.id }
  tags       = { service = "elasticache" }
}
