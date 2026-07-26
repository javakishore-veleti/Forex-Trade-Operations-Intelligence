# Design Document — OpenSearch (Observability Logging on AWS)

> **Stage 2 of 3** (`requirements.md → design.md → tasks.md`). Resolves `CloudTargetBinding`
> for `OBSERVABILITY_LOGGING` → AWS OpenSearch Service (replacing local ELK stack).

## 1. Overview

The platform `OBSERVABILITY_LOGGING` maps to **Amazon OpenSearch Service** replacing the local
Elasticsearch + Logstash + Kibana (ELK) stack. Fluent Bit runs as a DaemonSet to ship logs;
OpenSearch Dashboards replaces Kibana.

**Technology Bindings:**

| Technology Role | AWS Service | Configuration |
|---|---|---|
| `OBSERVABILITY_LOGGING` | Amazon OpenSearch Service | 2.11, Multi-AZ |
| Log shipper | Fluent Bit (DaemonSet on EKS) | Structured JSON → OpenSearch |
| Dashboards | OpenSearch Dashboards | Replaces Kibana |
| Encryption | KMS (at-rest) + HTTPS (in-transit) | Customer-managed CMK |
| Access control | Fine-grained access control (FGAC) | IAM + internal users |

## 2. Domain Configuration

```hcl
# DevOps/AWS/terraform/modules/opensearch/main.tf (conceptual)
resource "aws_opensearch_domain" "fxops" {
  domain_name    = "fxops-logs-${var.environment}"
  engine_version = "OpenSearch_2.11"

  cluster_config {
    instance_type            = var.environment == "prod" ? "r6g.large.search" : "t3.medium.search"
    instance_count           = var.environment == "prod" ? 2 : 1
    dedicated_master_enabled = var.environment == "prod" ? true : false
    dedicated_master_type    = "m6g.large.search"
    dedicated_master_count   = 3
    zone_awareness_enabled   = var.environment == "prod" ? true : false

    zone_awareness_config {
      availability_zone_count = 2
    }

    warm_enabled = var.environment == "prod" ? true : false
    warm_type    = "ultrawarm1.medium.search"
    warm_count   = 2
  }

  ebs_options {
    ebs_enabled = true
    volume_type = "gp3"
    volume_size = var.environment == "prod" ? 500 : 50
    iops        = var.environment == "prod" ? 3000 : null
    throughput  = var.environment == "prod" ? 250 : null
  }

  encrypt_at_rest { enabled = true; kms_key_id = var.kms_key_arn }
  node_to_node_encryption { enabled = true }

  domain_endpoint_options {
    enforce_https       = true
    tls_security_policy = "Policy-Min-TLS-1-2-PFS-2023-10"
  }

  vpc_options {
    subnet_ids         = var.environment == "prod" ? var.data_subnet_ids : [var.data_subnet_ids[0]]
    security_group_ids = [aws_security_group.opensearch.id]
  }

  advanced_security_options {
    enabled                        = true
    internal_user_database_enabled = true
    master_user_options {
      master_user_name     = "fxops_admin"
      master_user_password = var.opensearch_admin_password
    }
  }

  log_publishing_options {
    cloudwatch_log_group_arn = aws_cloudwatch_log_group.opensearch.arn
    log_type                 = "INDEX_SLOW_LOGS"
  }
}
```

## 3. Security Groups

```hcl
resource "aws_security_group" "opensearch" {
  name_prefix = "fxops-opensearch-"
  vpc_id      = var.vpc_id

  ingress {
    from_port       = 443
    to_port         = 443
    protocol        = "tcp"
    security_groups = [
      var.eks_services_sg_id,    # Fluent Bit DaemonSet
      var.eks_infra_sg_id        # Operators/dashboards access
    ]
  }

  egress { from_port = 0; to_port = 0; protocol = "-1"; cidr_blocks = ["0.0.0.0/0"] }
}
```

## 4. Fluent Bit DaemonSet (Log Shipper)

```yaml
# DevOps/AWS/helm/platform/fluent-bit/values.yaml
config:
  inputs: |
    [INPUT]
        Name              tail
        Path              /var/log/containers/fxops-*.log
        Parser            docker
        Tag               kube.*
        Refresh_Interval  5
        Mem_Buf_Limit     5MB

  filters: |
    [FILTER]
        Name         kubernetes
        Match        kube.*
        Merge_Log    On
        K8S-Logging.Parser On

  outputs: |
    [OUTPUT]
        Name            opensearch
        Match           *
        Host            ${OPENSEARCH_ENDPOINT}
        Port            443
        HTTP_User       ${OPENSEARCH_USER}
        HTTP_Passwd     ${OPENSEARCH_PASSWORD}
        tls             On
        Suppress_Type_Name On
        Index           fxops-logs-%Y.%m.%d
        Retry_Limit     5
        Buffer_Size     512KB
```

## 5. Index Lifecycle Policy (ISM)

```json
{
  "policy": {
    "description": "FXOps log retention: hot 7d → warm 23d → delete 30d",
    "default_state": "hot",
    "states": [
      {
        "name": "hot",
        "actions": [{ "rollover": { "min_size": "50gb", "min_index_age": "7d" } }],
        "transitions": [{ "state_name": "warm", "conditions": { "min_index_age": "7d" } }]
      },
      {
        "name": "warm",
        "actions": [
          { "warm_migration": {} },
          { "read_only": {} }
        ],
        "transitions": [{ "state_name": "delete", "conditions": { "min_index_age": "30d" } }]
      },
      {
        "name": "delete",
        "actions": [{ "delete": {} }]
      }
    ],
    "ism_template": [{ "index_patterns": ["fxops-logs-*"], "priority": 100 }]
  }
}
```

DLQ/audit indexes use a separate policy with 90-day retention.

## 6. Index Templates

```json
{
  "index_patterns": ["fxops-logs-*"],
  "template": {
    "settings": {
      "number_of_shards": 2,
      "number_of_replicas": 1,
      "index.codec": "best_compression"
    },
    "mappings": {
      "properties": {
        "timestamp":     { "type": "date", "format": "strict_date_optional_time" },
        "traceId":       { "type": "keyword" },
        "spanId":        { "type": "keyword" },
        "correlationId": { "type": "keyword" },
        "tradeId":       { "type": "keyword" },
        "service":       { "type": "keyword" },
        "level":         { "type": "keyword" },
        "message":       { "type": "text" },
        "logger":        { "type": "keyword" }
      }
    }
  }
}
```

## 7. Saved Queries Migration

Migrate from Kibana saved queries (from `05-observability/04-otel-log-correlation`):

| Local Kibana Query | OpenSearch Saved Search |
|---|---|
| Trade trace lookup | `tradeId:"FX-*" AND traceId:*` |
| Error investigation | `level:ERROR AND service:<name>` |
| Correlation chain | `correlationId:"<uuid>"` |
| DLQ events | `service:*dlq* OR message:*dead-letter*` |

Script: `DevOps/AWS/scripts/migrate-kibana-to-opensearch.py` — converts NDJSON export to OpenSearch format.

## 8. Monitoring

CloudWatch metrics for the OpenSearch domain:
- `ClusterStatus.red` → P1 alert
- `FreeStorageSpace` < 20% → storage alert
- `JVMMemoryPressure` > 80% → scale alert
- `IndexingRate` → capacity planning
- `SearchLatency` p95 > 5s → performance alert

## 9. Terraform Module Layout

```
DevOps/AWS/terraform/modules/opensearch/
├── main.tf          # OpenSearch domain
├── security.tf      # Security group, access policy
├── ism.tf           # Index lifecycle policy (provisioned via API)
├── monitoring.tf    # CloudWatch alarms
├── variables.tf
└── outputs.tf       # domain_endpoint, dashboards_endpoint, domain_arn
```
