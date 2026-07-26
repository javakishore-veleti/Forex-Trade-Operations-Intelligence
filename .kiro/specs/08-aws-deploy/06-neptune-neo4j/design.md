# Design Document — Neptune (Graph Store on AWS)

> **Stage 2 of 3** (`requirements.md → design.md → tasks.md`). Resolves `CloudTargetBinding`
> for `GRAPH_STORE` → AWS Neptune with openCypher. Includes self-managed Neo4j fallback.

## 1. Overview

The platform `GRAPH_STORE` maps to **Amazon Neptune** as the primary recommendation, using
openCypher for query compatibility with local Neo4j. A self-managed Neo4j on EKS fallback
is documented for cases where Neptune's openCypher subset is insufficient.

**Decision:** Neptune is preferred (managed, serverless scaling, integrated IAM). The ADR
at `docs/adr/0008-graph-store-aws.md` documents the trade-off.

**Technology Bindings:**

| Technology Role | AWS Service | Configuration |
|---|---|---|
| `GRAPH_STORE` (primary) | Amazon Neptune | openCypher, serverless or provisioned |
| `GRAPH_STORE` (fallback) | Neo4j 5.x on EKS | Helm chart, StatefulSet |
| Encryption | KMS at-rest + TLS in-transit | Customer-managed CMK |
| Authentication | IAM (Neptune) / password (Neo4j) | Via IRSA / Secrets Manager |
| Client | Spring Data Neo4j (openCypher) | Profile-switched |

## 2. Neptune Cluster Configuration

```hcl
# DevOps/AWS/terraform/modules/neptune/main.tf (conceptual)
resource "aws_neptune_cluster" "fxops" {
  cluster_identifier  = "fxops-graph-${var.environment}"
  engine              = "neptune"
  engine_version      = "1.3.2.0"
  
  vpc_security_group_ids         = [aws_security_group.neptune.id]
  neptune_subnet_group_name      = aws_neptune_subnet_group.data.name
  neptune_cluster_parameter_group_name = aws_neptune_cluster_parameter_group.fxops.name

  storage_encrypted = true
  kms_key_id        = var.kms_key_arn

  iam_database_authentication_enabled = true
  enable_cloudwatch_logs_exports      = ["audit"]

  backup_retention_period = var.environment == "prod" ? 7 : 1
  preferred_backup_window = "03:00-04:00"

  deletion_protection = var.environment == "prod" ? true : false
  skip_final_snapshot = var.environment != "prod"
}

resource "aws_neptune_cluster_instance" "primary" {
  cluster_identifier = aws_neptune_cluster.fxops.id
  instance_class     = var.environment == "prod" ? "db.r6g.large" : "db.t4g.medium"
  engine             = "neptune"
}

resource "aws_neptune_cluster_instance" "replica" {
  count              = var.environment == "prod" ? 1 : 0
  cluster_identifier = aws_neptune_cluster.fxops.id
  instance_class     = "db.r6g.large"
  engine             = "neptune"
}
```

## 3. Parameter Group

```hcl
resource "aws_neptune_cluster_parameter_group" "fxops" {
  family = "neptune1.3"
  name   = "fxops-neptune-params-${var.environment}"

  parameter { name = "neptune_enable_audit_log" value = "1" }
  parameter { name = "neptune_query_timeout"     value = "30000" }  # 30s max query
}
```

## 4. Security Groups

```hcl
resource "aws_security_group" "neptune" {
  name_prefix = "fxops-neptune-"
  vpc_id      = var.vpc_id

  ingress {
    from_port       = 8182
    to_port         = 8182
    protocol        = "tcp"
    security_groups = [var.eks_services_sg_id]
  }

  egress { from_port = 0; to_port = 0; protocol = "-1"; cidr_blocks = ["0.0.0.0/0"] }
}
```

## 5. openCypher Query Compatibility

Neptune supports openCypher with some differences from Neo4j:
- ✅ `MATCH`, `WHERE`, `RETURN`, `CREATE`, `MERGE`, `DELETE`
- ✅ Variable-length paths: `MATCH (a)-[*1..5]->(b)`
- ❌ APOC procedures → replace with Gremlin or application logic
- ❌ Neo4j-specific functions (`apoc.path.*`) → native traversal patterns

Example contagion query (works on both):
```cypher
MATCH path = (t:Trade {tradeId: 'FX-000001'})-[:SETTLES_WITH|BELONGS_TO*1..3]->(affected)
RETURN affected.tradeId AS affectedTrade, length(path) AS hops
ORDER BY hops
```

## 6. Data Migration Strategy

```bash
# Step 1: Export from local Neo4j (nodes + relationships as CSV)
# DevOps/AWS/scripts/neptune-export.sh
cypher-shell -d fxops "CALL apoc.export.csv.all('export.csv', {})"

# Step 2: Transform to Neptune bulk loader format
python3 DevOps/AWS/scripts/neo4j_to_neptune.py --input export.csv --output neptune/

# Step 3: Upload to S3 and trigger Neptune bulk load
aws s3 cp neptune/ s3://fxops-graph-data-<env>/load/ --recursive
aws neptune start-loader --cluster <ENDPOINT> --source s3://fxops-graph-data-<env>/load/
```

Alternative (openCypher-based): replay `CREATE` statements from a Cypher dump file.

## 7. Self-Managed Neo4j Fallback (EKS)

If Neptune compatibility gaps block critical queries:

```yaml
# DevOps/AWS/helm/platform/neo4j/values-prod.yaml
neo4j:
  name: fxops-graph
  edition: community  # or enterprise with license
  resources:
    cpu: "2"
    memory: "8Gi"
  volumes:
    data:
      mode: dynamic
      dynamic:
        storageClassName: gp3
        requests:
          storage: 50Gi
  config:
    server.default_listen_address: "0.0.0.0"
    dbms.security.auth_enabled: "true"
    dbms.connector.bolt.tls_level: REQUIRED
```

## 8. Connection Pattern for Services

```yaml
# Helm values — Neptune profile
spring:
  neo4j:
    uri: bolt+s://${NEPTUNE_CLUSTER_ENDPOINT}:8182
    authentication:
      username: ""  # IAM auth — no username
      password: ""  # IAM auth — signed request
    # Custom: Neptune IAM auth requires a SigV4 signing plugin

# Helm values — Neo4j on EKS profile
spring:
  neo4j:
    uri: bolt+s://fxops-graph-neo4j.fxops-services:7687
    authentication:
      username: neo4j
      password: ${NEO4J_PASSWORD}
```

## 9. Terraform Module Layout

```
DevOps/AWS/terraform/modules/neptune/
├── main.tf          # Neptune cluster + instances
├── security.tf      # Security group
├── params.tf        # Parameter group
├── monitoring.tf    # CloudWatch alarms
├── variables.tf
└── outputs.tf       # cluster_endpoint, reader_endpoint, port
```
