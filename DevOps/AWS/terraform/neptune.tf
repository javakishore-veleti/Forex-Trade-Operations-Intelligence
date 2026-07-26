# =============================================================================
# FXOps Platform — Neptune (Graph Store, openCypher)
# =============================================================================

# --- Subnet Group ---
resource "aws_neptune_subnet_group" "data" {
  name       = "fxops-neptune-${var.environment}"
  subnet_ids = aws_subnet.data[*].id
  tags       = { Name = "fxops-neptune-subnet-group" }
}

# --- Security Group ---
resource "aws_security_group" "neptune" {
  name_prefix = "fxops-neptune-"
  description = "Neptune - only accessible from EKS services"
  vpc_id      = aws_vpc.main.id

  ingress {
    from_port       = 8182
    to_port         = 8182
    protocol        = "tcp"
    security_groups = [aws_security_group.eks_services.id]
  }

  egress {
    from_port   = 0
    to_port     = 0
    protocol    = "-1"
    cidr_blocks = ["0.0.0.0/0"]
  }

  tags = { Name = "fxops-neptune-${var.environment}" }
}

# --- Parameter Group ---
resource "aws_neptune_cluster_parameter_group" "fxops" {
  family = "neptune1.3"
  name   = "fxops-neptune-params-${var.environment}"

  parameter {
    name  = "neptune_enable_audit_log"
    value = "1"
  }
  parameter {
    name  = "neptune_query_timeout"
    value = "30000"
  }

  tags = { service = "neptune" }
}

# --- Neptune Cluster ---
resource "aws_neptune_cluster" "fxops" {
  cluster_identifier = "fxops-graph-${var.environment}"
  engine             = "neptune"
  engine_version     = "1.3.2.0"

  vpc_security_group_ids               = [aws_security_group.neptune.id]
  neptune_subnet_group_name            = aws_neptune_subnet_group.data.name
  neptune_cluster_parameter_group_name = aws_neptune_cluster_parameter_group.fxops.name

  storage_encrypted = true
  kms_key_id        = aws_kms_key.fxops.arn

  iam_database_authentication_enabled = true
  enable_cloudwatch_logs_exports      = ["audit"]

  backup_retention_period = var.environment == "prod" ? 7 : 1
  preferred_backup_window = "03:00-04:00"

  deletion_protection = var.environment == "prod" ? true : false
  skip_final_snapshot = var.environment != "prod"

  tags = { service = "neptune" }
}

# --- Primary Instance ---
resource "aws_neptune_cluster_instance" "primary" {
  identifier         = "fxops-graph-${var.environment}-primary"
  cluster_identifier = aws_neptune_cluster.fxops.id
  instance_class     = var.neptune_instance_class
  engine             = "neptune"

  tags = { service = "neptune" }
}

# --- Replica Instance(s) ---
resource "aws_neptune_cluster_instance" "replica" {
  count              = var.neptune_replica_count
  identifier         = "fxops-graph-${var.environment}-replica-${count.index}"
  cluster_identifier = aws_neptune_cluster.fxops.id
  instance_class     = var.neptune_instance_class
  engine             = "neptune"

  tags = { service = "neptune" }
}

# --- CloudWatch Alarms ---
resource "aws_cloudwatch_metric_alarm" "neptune_cpu" {
  alarm_name          = "fxops-neptune-cpu-${var.environment}"
  comparison_operator = "GreaterThanThreshold"
  evaluation_periods  = 3
  metric_name         = "CPUUtilization"
  namespace           = "AWS/Neptune"
  period              = 300
  statistic           = "Average"
  threshold           = 80
  alarm_description   = "Neptune CPU > 80%"

  dimensions = { DBClusterIdentifier = aws_neptune_cluster.fxops.cluster_identifier }
  tags       = { service = "neptune" }
}

resource "aws_cloudwatch_metric_alarm" "neptune_memory" {
  alarm_name          = "fxops-neptune-memory-${var.environment}"
  comparison_operator = "LessThanThreshold"
  evaluation_periods  = 3
  metric_name         = "FreeableMemory"
  namespace           = "AWS/Neptune"
  period              = 300
  statistic           = "Average"
  threshold           = 1073741824 # 1 GB in bytes
  alarm_description   = "Neptune freeable memory < 1 GB"

  dimensions = { DBClusterIdentifier = aws_neptune_cluster.fxops.cluster_identifier }
  tags       = { service = "neptune" }
}
