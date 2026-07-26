# Design Document — MSK Kafka (Event Stream on AWS)

> **Stage 2 of 3** (`requirements.md → design.md → tasks.md`). Resolves `CloudTargetBinding`
> for `EVENT_STREAM` → Amazon MSK. Concrete Terraform configuration.

## 1. Overview

The platform `EVENT_STREAM` maps to **Amazon MSK (Managed Streaming for Apache Kafka)** running
Kafka 3.6.x with IAM authentication, TLS encryption, and tiered storage.

**Technology Bindings:**

| Technology Role | AWS Service | Configuration |
|---|---|---|
| `EVENT_STREAM` | Amazon MSK | 3.6.x, provisioned, 3 brokers |
| Schema Registry | AWS Glue Schema Registry | JSON Schema, BACKWARD compat |
| Monitoring | MSK CloudWatch metrics + Prometheus JMX exporter | Open monitoring enabled |
| Topic provisioning | Lambda or EKS Job (idempotent) | Reads `topic-registry.yml` |

## 2. Cluster Configuration

```hcl
# DevOps/AWS/terraform/modules/msk/main.tf (conceptual)
resource "aws_msk_cluster" "fxops" {
  cluster_name           = "fxops-${var.environment}"
  kafka_version          = "3.6.0"
  number_of_broker_nodes = var.environment == "prod" ? 3 : 2

  broker_node_group_info {
    instance_type   = var.environment == "prod" ? "kafka.m7g.large" : "kafka.t3.small"
    client_subnets  = var.data_subnet_ids
    security_groups = [aws_security_group.msk.id]

    storage_info {
      ebs_storage_info {
        volume_size = 100
        provisioned_throughput {
          enabled    = var.environment == "prod"
          volume_throughput = 250  # MiB/s
        }
      }
    }
  }

  encryption_info {
    encryption_in_transit {
      client_broker = "TLS"
      in_cluster    = true
    }
    encryption_at_rest_kms_key_arn = var.kms_key_arn
  }

  client_authentication {
    unauthenticated = false
    sasl { iam = true }
  }

  open_monitoring {
    prometheus {
      jmx_exporter  { enabled_in_broker = true }
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
}
```

## 3. Broker Configuration (MSK Configuration)

```hcl
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
}
```

## 4. Security Groups

```hcl
resource "aws_security_group" "msk" {
  name_prefix = "fxops-msk-"
  vpc_id      = var.vpc_id

  ingress {
    from_port       = 9098      # IAM auth TLS port
    to_port         = 9098
    protocol        = "tcp"
    security_groups = [var.eks_services_sg_id]
  }

  ingress {
    from_port       = 9098
    to_port         = 9098
    protocol        = "tcp"
    security_groups = [var.eks_agents_sg_id]  # n8n webhook → not direct; via services only
  }
}
```

## 5. Topic Provisioning Automation

A Kubernetes Job runs post-deployment to sync topics from `topic-registry.yml`:

```yaml
# DevOps/AWS/helm/platform/msk-topic-provisioner/job.yaml
apiVersion: batch/v1
kind: Job
metadata:
  name: msk-topic-provisioner
  namespace: fxops-infra
spec:
  template:
    spec:
      serviceAccountName: msk-admin  # IRSA with kafka-cluster:* permissions
      containers:
        - name: provisioner
          image: <ACCOUNT_ID>.dkr.ecr.<REGION>.amazonaws.com/fxops-topic-provisioner:1.0.0
          command: ["python", "provision_topics.py"]
          env:
            - name: BOOTSTRAP_SERVERS
              value: "${MSK_BOOTSTRAP_BROKERS}"
            - name: TOPIC_REGISTRY_PATH
              value: /config/topic-registry.yml
          volumeMounts:
            - name: config
              mountPath: /config
      volumes:
        - name: config
          configMap: { name: topic-registry }
      restartPolicy: OnFailure
```

## 6. Glue Schema Registry

```hcl
resource "aws_glue_registry" "fxops" {
  registry_name = "fxops-events-${var.environment}"
}

# One schema per domain topic value
resource "aws_glue_schema" "trade_events" {
  schema_name       = "fxops.trade.events-value"
  registry_arn      = aws_glue_registry.fxops.arn
  data_format       = "JSON"
  compatibility     = "BACKWARD"
  schema_definition = file("${path.module}/schemas/trade-events-value.json")
}
```

## 7. IAM Policies for Service Access

```json
{
  "Effect": "Allow",
  "Action": [
    "kafka-cluster:Connect",
    "kafka-cluster:DescribeTopic",
    "kafka-cluster:ReadData",
    "kafka-cluster:WriteData",
    "kafka-cluster:DescribeGroup",
    "kafka-cluster:AlterGroup"
  ],
  "Resource": [
    "arn:aws:kafka:<REGION>:<ACCOUNT_ID>:cluster/fxops-*/*",
    "arn:aws:kafka:<REGION>:<ACCOUNT_ID>:topic/fxops-*/*/<ALLOWED_TOPICS>",
    "arn:aws:kafka:<REGION>:<ACCOUNT_ID>:group/fxops-*/*/*"
  ]
}
```

Per-service policies scope `<ALLOWED_TOPICS>` to only that service's declared produce/consume topics.

## 8. Connection String Pattern for Services

```yaml
# Helm values (per-service, Spring Boot)
spring:
  kafka:
    bootstrap-servers: ${MSK_BOOTSTRAP_BROKERS}:9098
    properties:
      security.protocol: SASL_SSL
      sasl.mechanism: AWS_MSK_IAM
      sasl.jaas.config: software.amazon.msk.auth.iam.IAMLoginModule required;
      sasl.client.callback.handler.class: software.amazon.msk.auth.iam.IAMClientCallbackHandler
    ssl:
      trust-store-location: /etc/ssl/kafka-truststore.jks
```

## 9. Terraform Module Layout

```
DevOps/AWS/terraform/modules/msk/
├── main.tf            # MSK cluster, configuration
├── security.tf        # Security groups
├── glue.tf            # Glue Schema Registry + schemas
├── monitoring.tf      # CloudWatch log group, alarms
├── variables.tf
└── outputs.tf         # bootstrap_brokers, cluster_arn, registry_arn
```
