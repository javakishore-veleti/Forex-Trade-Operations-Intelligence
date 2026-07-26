# =============================================================================
# FXOps Platform — RDS PostgreSQL 16, Parameter Group, RDS Proxy
# =============================================================================

# --- DB Subnet Group ---
resource "aws_db_subnet_group" "data" {
  name       = "fxops-data-${var.environment}"
  subnet_ids = aws_subnet.data[*].id
  tags       = { Name = "fxops-data-subnet-group" }
}

# --- RDS Security Group ---
resource "aws_security_group" "rds" {
  name_prefix = "fxops-rds-"
  description = "RDS - only accessible from RDS Proxy"
  vpc_id      = aws_vpc.main.id

  ingress {
    from_port       = 5432
    to_port         = 5432
    protocol        = "tcp"
    security_groups = [aws_security_group.rds_proxy.id]
  }

  egress {
    from_port   = 0
    to_port     = 0
    protocol    = "-1"
    cidr_blocks = ["0.0.0.0/0"]
  }

  tags = { Name = "fxops-rds-${var.environment}" }
}

resource "aws_security_group" "rds_proxy" {
  name_prefix = "fxops-rds-proxy-"
  description = "RDS Proxy - only accessible from EKS services"
  vpc_id      = aws_vpc.main.id

  ingress {
    from_port       = 5432
    to_port         = 5432
    protocol        = "tcp"
    security_groups = [aws_security_group.eks_services.id]
  }

  egress {
    from_port   = 0
    to_port     = 0
    protocol    = "-1"
    cidr_blocks = ["0.0.0.0/0"]
  }

  tags = { Name = "fxops-rds-proxy-${var.environment}" }
}

# --- Parameter Group ---
resource "aws_db_parameter_group" "fxops" {
  name   = "fxops-pg16-${var.environment}"
  family = "postgres16"

  parameter {
    name  = "shared_buffers"
    value = "{DBInstanceClassMemory/4}"
  }
  parameter {
    name  = "effective_cache_size"
    value = "{DBInstanceClassMemory*3/4}"
  }
  parameter {
    name  = "work_mem"
    value = "65536"
  }
  parameter {
    name  = "checkpoint_completion_target"
    value = "0.9"
  }
  parameter {
    name  = "max_connections"
    value = "200"
  }
  parameter {
    name  = "log_min_duration_statement"
    value = "500"
  }
  parameter {
    name  = "log_statement"
    value = "ddl"
  }
  parameter {
    name  = "rds.force_ssl"
    value = "1"
  }

  tags = { service = "rds" }
}

# --- RDS Instance ---
resource "aws_db_instance" "fxops" {
  identifier     = "fxops-${var.environment}"
  engine         = "postgres"
  engine_version = "16.4"
  instance_class = var.rds_instance_class

  multi_az              = var.rds_multi_az
  allocated_storage     = 100
  max_allocated_storage = 500
  storage_type          = var.rds_storage_type
  storage_encrypted     = true
  kms_key_id            = aws_kms_key.fxops.arn

  db_subnet_group_name   = aws_db_subnet_group.data.name
  vpc_security_group_ids = [aws_security_group.rds.id]
  publicly_accessible    = false

  db_name                     = "fxops"
  username                    = "fxops_admin"
  manage_master_user_password = true

  parameter_group_name    = aws_db_parameter_group.fxops.name
  backup_retention_period = 30
  backup_window           = "03:00-04:00"
  maintenance_window      = "sun:04:30-sun:05:30"
  copy_tags_to_snapshot   = true

  enabled_cloudwatch_logs_exports = ["postgresql", "upgrade"]
  performance_insights_enabled    = true
  deletion_protection             = var.environment == "prod" ? true : false

  tags = { service = "rds-postgres" }
}

# --- RDS Proxy IAM Role ---
resource "aws_iam_role" "rds_proxy" {
  name = "fxops-rds-proxy-${var.environment}"

  assume_role_policy = jsonencode({
    Version = "2012-10-17"
    Statement = [{
      Action    = "sts:AssumeRole"
      Effect    = "Allow"
      Principal = { Service = "rds.amazonaws.com" }
    }]
  })
}

resource "aws_iam_role_policy" "rds_proxy_secrets" {
  name = "secrets-access"
  role = aws_iam_role.rds_proxy.id

  policy = jsonencode({
    Version = "2012-10-17"
    Statement = [{
      Effect   = "Allow"
      Action   = ["secretsmanager:GetSecretValue", "secretsmanager:DescribeSecret"]
      Resource = [aws_db_instance.fxops.master_user_secret[0].secret_arn]
    }]
  })
}

# --- RDS Proxy ---
resource "aws_db_proxy" "fxops" {
  name                   = "fxops-proxy-${var.environment}"
  engine_family          = "POSTGRESQL"
  role_arn               = aws_iam_role.rds_proxy.arn
  vpc_subnet_ids         = aws_subnet.data[*].id
  vpc_security_group_ids = [aws_security_group.rds_proxy.id]
  require_tls            = true

  auth {
    auth_scheme = "SECRETS"
    secret_arn  = aws_db_instance.fxops.master_user_secret[0].secret_arn
    iam_auth    = "REQUIRED"
  }

  tags = { service = "rds-proxy" }
}

resource "aws_db_proxy_default_target_group" "fxops" {
  db_proxy_name = aws_db_proxy.fxops.name

  connection_pool_config {
    max_connections_percent      = 80
    max_idle_connections_percent = 50
    connection_borrow_timeout    = 120
  }
}

resource "aws_db_proxy_target" "fxops" {
  db_proxy_name          = aws_db_proxy.fxops.name
  target_group_name      = aws_db_proxy_default_target_group.fxops.name
  db_instance_identifier = aws_db_instance.fxops.identifier
}
