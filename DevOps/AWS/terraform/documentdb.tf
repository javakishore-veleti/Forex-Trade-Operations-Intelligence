# =============================================================================
# FXOps Platform — DocumentDB (MongoDB 7.0 Compatible)
# =============================================================================

# --- Subnet Group ---
resource "aws_docdb_subnet_group" "data" {
  name       = "fxops-docdb-${var.environment}"
  subnet_ids = aws_subnet.data[*].id
  tags       = { Name = "fxops-docdb-subnet-group" }
}

# --- Security Group ---
resource "aws_security_group" "docdb" {
  name_prefix = "fxops-docdb-"
  description = "DocumentDB - only accessible from EKS services"
  vpc_id      = aws_vpc.main.id

  ingress {
    from_port       = 27017
    to_port         = 27017
    protocol        = "tcp"
    security_groups = [aws_security_group.eks_services.id]
  }

  egress {
    from_port   = 0
    to_port     = 0
    protocol    = "-1"
    cidr_blocks = ["0.0.0.0/0"]
  }

  tags = { Name = "fxops-docdb-${var.environment}" }
}

# --- Parameter Group ---
resource "aws_docdb_cluster_parameter_group" "fxops" {
  family = "docdb7.0"
  name   = "fxops-docdb-params-${var.environment}"

  parameter {
    name  = "tls"
    value = "enabled"
  }
  parameter {
    name  = "audit_logs"
    value = "enabled"
  }
  parameter {
    name  = "profiler"
    value = "enabled"
  }
  parameter {
    name  = "profiler_threshold_ms"
    value = "100"
  }

  tags = { service = "documentdb" }
}

# --- Master Password ---
resource "random_password" "docdb_master" {
  length  = 32
  special = false
}

# --- DocumentDB Cluster ---
resource "aws_docdb_cluster" "fxops" {
  cluster_identifier     = "fxops-docdb-${var.environment}"
  engine                 = "docdb"
  engine_version         = "7.0.0"
  master_username        = "fxops_admin"
  master_password        = random_password.docdb_master.result
  db_subnet_group_name   = aws_docdb_subnet_group.data.name
  vpc_security_group_ids = [aws_security_group.docdb.id]

  storage_encrypted = true
  kms_key_id        = aws_kms_key.fxops.arn

  backup_retention_period = var.environment == "prod" ? 7 : 1
  preferred_backup_window = "03:00-04:00"

  enabled_cloudwatch_logs_exports = ["audit", "profiler"]
  deletion_protection             = var.environment == "prod" ? true : false

  db_cluster_parameter_group_name = aws_docdb_cluster_parameter_group.fxops.name

  tags = { service = "documentdb" }
}

# --- Primary Instance ---
resource "aws_docdb_cluster_instance" "primary" {
  identifier         = "fxops-docdb-${var.environment}-primary"
  cluster_identifier = aws_docdb_cluster.fxops.id
  instance_class     = var.docdb_instance_class

  tags = { service = "documentdb" }
}

# --- Replica Instance(s) ---
resource "aws_docdb_cluster_instance" "replica" {
  count              = var.docdb_replica_count
  identifier         = "fxops-docdb-${var.environment}-replica-${count.index}"
  cluster_identifier = aws_docdb_cluster.fxops.id
  instance_class     = var.docdb_instance_class

  tags = { service = "documentdb" }
}

# --- CloudWatch Alarms ---
resource "aws_cloudwatch_metric_alarm" "docdb_cpu" {
  alarm_name          = "fxops-docdb-cpu-${var.environment}"
  comparison_operator = "GreaterThanThreshold"
  evaluation_periods  = 3
  metric_name         = "CPUUtilization"
  namespace           = "AWS/DocDB"
  period              = 300
  statistic           = "Average"
  threshold           = 80
  alarm_description   = "DocumentDB CPU > 80%"

  dimensions = { DBClusterIdentifier = aws_docdb_cluster.fxops.cluster_identifier }
  tags       = { service = "documentdb" }
}

resource "aws_cloudwatch_metric_alarm" "docdb_memory" {
  alarm_name          = "fxops-docdb-memory-${var.environment}"
  comparison_operator = "LessThanThreshold"
  evaluation_periods  = 3
  metric_name         = "FreeableMemory"
  namespace           = "AWS/DocDB"
  period              = 300
  statistic           = "Average"
  threshold           = 1073741824 # 1 GB in bytes
  alarm_description   = "DocumentDB freeable memory < 1 GB"

  dimensions = { DBClusterIdentifier = aws_docdb_cluster.fxops.cluster_identifier }
  tags       = { service = "documentdb" }
}
