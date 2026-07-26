# =============================================================================
# FXOps Platform — Outputs (connection strings, endpoints)
# =============================================================================

# --- EKS ---
output "eks_cluster_endpoint" {
  description = "EKS cluster API server endpoint"
  value       = aws_eks_cluster.fxops.endpoint
}

output "eks_cluster_name" {
  description = "EKS cluster name"
  value       = aws_eks_cluster.fxops.name
}

output "eks_oidc_provider_arn" {
  description = "EKS OIDC provider ARN for IRSA"
  value       = aws_iam_openid_connect_provider.eks.arn
}

# --- RDS ---
output "rds_proxy_endpoint" {
  description = "RDS Proxy endpoint for service connections"
  value       = aws_db_proxy.fxops.endpoint
}

output "rds_instance_endpoint" {
  description = "Direct RDS instance endpoint (admin only)"
  value       = aws_db_instance.fxops.endpoint
  sensitive   = true
}

# --- MSK ---
output "msk_bootstrap_brokers" {
  description = "MSK bootstrap brokers (IAM SASL/TLS)"
  value       = aws_msk_cluster.fxops.bootstrap_brokers_sasl_iam
}

output "msk_cluster_arn" {
  description = "MSK cluster ARN"
  value       = aws_msk_cluster.fxops.arn
}

output "glue_registry_arn" {
  description = "Glue Schema Registry ARN"
  value       = aws_glue_registry.fxops.arn
}

# --- ElastiCache Redis ---
output "redis_configuration_endpoint" {
  description = "ElastiCache Redis cluster configuration endpoint"
  value       = aws_elasticache_replication_group.fxops.configuration_endpoint_address
}

output "redis_auth_token_secret_arn" {
  description = "Secrets Manager ARN for Redis AUTH token"
  value       = aws_secretsmanager_secret.redis_auth.arn
  sensitive   = true
}

# --- DocumentDB ---
output "docdb_cluster_endpoint" {
  description = "DocumentDB cluster endpoint (write)"
  value       = aws_docdb_cluster.fxops.endpoint
}

output "docdb_reader_endpoint" {
  description = "DocumentDB reader endpoint (read replicas)"
  value       = aws_docdb_cluster.fxops.reader_endpoint
}

output "docdb_master_secret_arn" {
  description = "Secrets Manager ARN for DocumentDB master password"
  value       = aws_secretsmanager_secret.docdb_master.arn
  sensitive   = true
}

# --- Neptune ---
output "neptune_cluster_endpoint" {
  description = "Neptune cluster endpoint (openCypher)"
  value       = aws_neptune_cluster.fxops.endpoint
}

output "neptune_reader_endpoint" {
  description = "Neptune reader endpoint"
  value       = aws_neptune_cluster.fxops.reader_endpoint
}

# --- OpenSearch ---
output "opensearch_domain_endpoint" {
  description = "OpenSearch domain endpoint (HTTPS)"
  value       = aws_opensearch_domain.fxops.endpoint
}

output "opensearch_dashboards_endpoint" {
  description = "OpenSearch Dashboards endpoint"
  value       = "${aws_opensearch_domain.fxops.endpoint}/_dashboards"
}

output "opensearch_admin_secret_arn" {
  description = "Secrets Manager ARN for OpenSearch admin password"
  value       = aws_secretsmanager_secret.opensearch_admin.arn
  sensitive   = true
}

# --- VPC ---
output "vpc_id" {
  description = "VPC ID"
  value       = aws_vpc.main.id
}

output "private_subnet_ids" {
  description = "Private subnet IDs (EKS nodes)"
  value       = aws_subnet.private[*].id
}

output "data_subnet_ids" {
  description = "Data subnet IDs (managed data services)"
  value       = aws_subnet.data[*].id
}
