# =============================================================================
# FXOps Platform — OpenSearch Service (Observability Logging)
# =============================================================================

# --- Security Group ---
resource "aws_security_group" "opensearch" {
  name_prefix = "fxops-opensearch-"
  description = "OpenSearch - accessible from EKS infra (Fluent Bit) and services"
  vpc_id      = aws_vpc.main.id

  ingress {
    from_port = 443
    to_port   = 443
    protocol  = "tcp"
    security_groups = [
      aws_security_group.eks_services.id,
      aws_security_group.eks_infra.id,
    ]
  }

  egress {
    from_port   = 0
    to_port     = 0
    protocol    = "-1"
    cidr_blocks = ["0.0.0.0/0"]
  }

  tags = { Name = "fxops-opensearch-${var.environment}" }
}

# --- CloudWatch Log Group ---
resource "aws_cloudwatch_log_group" "opensearch" {
  name              = "/aws/opensearch/fxops-${var.environment}"
  retention_in_days = 90
  tags              = { service = "opensearch" }
}

# --- CloudWatch Log Resource Policy ---
resource "aws_cloudwatch_log_resource_policy" "opensearch" {
  policy_name = "fxops-opensearch-logs-${var.environment}"
  policy_document = jsonencode({
    Version = "2012-10-17"
    Statement = [{
      Effect    = "Allow"
      Principal = { Service = "es.amazonaws.com" }
      Action    = ["logs:PutLogEvents", "logs:CreateLogStream"]
      Resource  = "${aws_cloudwatch_log_group.opensearch.arn}:*"
    }]
  })
}

# --- OpenSearch Admin Password ---
resource "random_password" "opensearch_admin" {
  length           = 32
  special          = true
  override_special = "!@#%"
}

# --- OpenSearch Domain ---
resource "aws_opensearch_domain" "fxops" {
  domain_name    = "fxops-logs-${var.environment}"
  engine_version = "OpenSearch_2.11"

  cluster_config {
    instance_type            = var.opensearch_instance_type
    instance_count           = var.opensearch_instance_count
    dedicated_master_enabled = var.environment == "prod" ? true : false
    dedicated_master_type    = "m6g.large.search"
    dedicated_master_count   = var.environment == "prod" ? 3 : 0
    zone_awareness_enabled   = var.opensearch_instance_count > 1 ? true : false

    dynamic "zone_awareness_config" {
      for_each = var.opensearch_instance_count > 1 ? [1] : []
      content {
        availability_zone_count = 2
      }
    }

    warm_enabled = var.environment == "prod" ? true : false
    warm_type    = "ultrawarm1.medium.search"
    warm_count   = var.environment == "prod" ? 2 : 0
  }

  ebs_options {
    ebs_enabled = true
    volume_type = "gp3"
    volume_size = var.opensearch_ebs_volume_size
    iops        = var.environment == "prod" ? 3000 : null
    throughput  = var.environment == "prod" ? 250 : null
  }

  encrypt_at_rest {
    enabled    = true
    kms_key_id = aws_kms_key.fxops.key_id
  }

  node_to_node_encryption {
    enabled = true
  }

  domain_endpoint_options {
    enforce_https       = true
    tls_security_policy = "Policy-Min-TLS-1-2-PFS-2023-10"
  }

  vpc_options {
    subnet_ids         = var.opensearch_instance_count > 1 ? slice(aws_subnet.data[*].id, 0, 2) : [aws_subnet.data[0].id]
    security_group_ids = [aws_security_group.opensearch.id]
  }

  advanced_security_options {
    enabled                        = true
    internal_user_database_enabled = true
    master_user_options {
      master_user_name     = "fxops_admin"
      master_user_password = random_password.opensearch_admin.result
    }
  }

  log_publishing_options {
    cloudwatch_log_group_arn = aws_cloudwatch_log_group.opensearch.arn
    log_type                 = "INDEX_SLOW_LOGS"
  }

  tags = { service = "opensearch" }
}

# --- CloudWatch Alarms ---
resource "aws_cloudwatch_metric_alarm" "opensearch_red" {
  alarm_name          = "fxops-opensearch-red-${var.environment}"
  comparison_operator = "GreaterThanOrEqualToThreshold"
  evaluation_periods  = 1
  metric_name         = "ClusterStatus.red"
  namespace           = "AWS/ES"
  period              = 60
  statistic           = "Maximum"
  threshold           = 1
  alarm_description   = "OpenSearch cluster status RED - P1"

  dimensions = { DomainName = aws_opensearch_domain.fxops.domain_name }
  tags       = { service = "opensearch" }
}

resource "aws_cloudwatch_metric_alarm" "opensearch_storage" {
  alarm_name          = "fxops-opensearch-storage-${var.environment}"
  comparison_operator = "LessThanThreshold"
  evaluation_periods  = 2
  metric_name         = "FreeStorageSpace"
  namespace           = "AWS/ES"
  period              = 300
  statistic           = "Minimum"
  threshold           = var.opensearch_ebs_volume_size * 1024 * 0.2 # 20% free
  alarm_description   = "OpenSearch free storage < 20%"

  dimensions = { DomainName = aws_opensearch_domain.fxops.domain_name }
  tags       = { service = "opensearch" }
}

resource "aws_cloudwatch_metric_alarm" "opensearch_jvm" {
  alarm_name          = "fxops-opensearch-jvm-${var.environment}"
  comparison_operator = "GreaterThanThreshold"
  evaluation_periods  = 3
  metric_name         = "JVMMemoryPressure"
  namespace           = "AWS/ES"
  period              = 300
  statistic           = "Average"
  threshold           = 80
  alarm_description   = "OpenSearch JVM memory pressure > 80%"

  dimensions = { DomainName = aws_opensearch_domain.fxops.domain_name }
  tags       = { service = "opensearch" }
}
