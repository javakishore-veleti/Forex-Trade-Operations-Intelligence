# =============================================================================
# FXOps Platform — MSK Kafka Cluster, Configuration, Glue Schema Registry
# =============================================================================

# --- MSK Security Group ---
resource "aws_security_group" "msk" {
  name_prefix = "fxops-msk-"
  description = "MSK - only accessible from EKS services"
  vpc_id      = aws_vpc.main.id

  ingress {
    description     = "IAM SASL/TLS from services"
    from_port       = 9098
    to_port         = 9098
    protocol        = "tcp"
    security_groups = [aws_security_group.eks_services.id]
  }

  ingress {
    description = "Inter-broker communication"
    from_port   = 9098
    to_port     = 9098
    protocol    = "tcp"
    self        = true
  }

  egress {
    from_port   = 0
    to_port     = 0
    protocol    = "-1"
    cidr_blocks = ["0.0.0.0/0"]
  }

  tags = { Name = "fxops-msk-${var.environment}" }
}

# --- MSK Configuration ---
resource "aws_msk_configuration" "fxops" {
  name              = "fxops-config-${var.environment}"
  kafka_versions    = ["3.6.0"]
  server_properties = <<-EOT
    auto.create.topics.enable=false
    default.replication.factor=3
    min.insync.replicas=2
    num.partitions=6
    log.retention.hours=720
    log.segment.bytes=1073741824
    message.max.bytes=1048576
    replica.lag.time.max.ms=30000
    unclean.leader.election.enable=false
    log.cleanup.policy=delete
  EOT

  lifecycle {
    create_before_destroy = true
  }
}

# --- CloudWatch Log Group for MSK ---
resource "aws_cloudwatch_log_group" "msk" {
  name              = "/aws/msk/fxops-${var.environment}"
  retention_in_days = 90
  tags              = { service = "msk" }
}

# --- MSK Cluster ---
resource "aws_msk_cluster" "fxops" {
  cluster_name           = "fxops-${var.environment}"
  kafka_version          = "3.6.0"
  number_of_broker_nodes = var.msk_broker_count

  broker_node_group_info {
    instance_type   = var.msk_instance_type
    client_subnets  = slice(aws_subnet.data[*].id, 0, var.msk_broker_count > 2 ? 3 : 2)
    security_groups = [aws_security_group.msk.id]

    storage_info {
      ebs_storage_info {
        volume_size = 100
      }
    }
  }

  encryption_info {
    encryption_in_transit {
      client_broker = "TLS"
      in_cluster    = true
    }
    encryption_at_rest_kms_key_arn = aws_kms_key.fxops.arn
  }

  client_authentication {
    unauthenticated = false
    sasl { iam = true }
  }

  open_monitoring {
    prometheus {
      jmx_exporter { enabled_in_broker = true }
      node_exporter { enabled_in_broker = true }
    }
  }

  logging_info {
    broker_logs {
      cloudwatch_logs {
        enabled   = true
        log_group = aws_cloudwatch_log_group.msk.name
      }
    }
  }

  configuration_info {
    arn      = aws_msk_configuration.fxops.arn
    revision = aws_msk_configuration.fxops.latest_revision
  }

  tags = { service = "msk-kafka" }
}

# --- Glue Schema Registry ---
resource "aws_glue_registry" "fxops" {
  registry_name = "fxops-events-${var.environment}"
  description   = "FXOps domain event schemas (JSON Schema, BACKWARD compat)"
}

resource "aws_glue_schema" "trade_events" {
  schema_name   = "fxops.trade.events-value"
  registry_arn  = aws_glue_registry.fxops.arn
  data_format   = "JSON"
  compatibility = "BACKWARD"
  schema_definition = jsonencode({
    "$schema" = "http://json-schema.org/draft-07/schema#"
    title     = "FXOps Trade Event"
    type      = "object"
    properties = {
      eventId       = { type = "string", description = "Unique event ID (e.g., FX-EVT-000001)" }
      tradeId       = { type = "string", description = "Trade reference (e.g., FX-000001)" }
      eventType     = { type = "string" }
      occurredAt    = { type = "string", format = "date-time" }
      correlationId = { type = "string" }
    }
    required = ["eventId", "tradeId", "eventType", "occurredAt"]
  })
}

# --- CloudWatch Alarms ---
resource "aws_cloudwatch_metric_alarm" "msk_under_replicated" {
  alarm_name          = "fxops-msk-under-replicated-${var.environment}"
  comparison_operator = "GreaterThanThreshold"
  evaluation_periods  = 2
  metric_name         = "UnderReplicatedPartitions"
  namespace           = "AWS/Kafka"
  period              = 300
  statistic           = "Maximum"
  threshold           = 0
  alarm_description   = "MSK has under-replicated partitions"

  dimensions = {
    "Cluster Name" = aws_msk_cluster.fxops.cluster_name
  }

  tags = { service = "msk" }
}
