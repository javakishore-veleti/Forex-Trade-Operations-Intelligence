# =============================================================================
# FXOps Platform — Input Variables
# =============================================================================

variable "aws_region" {
  description = "AWS region for all resources"
  type        = string
  default     = "us-east-1"
}

variable "environment" {
  description = "Deployment environment (dev, staging, prod)"
  type        = string
  validation {
    condition     = contains(["dev", "staging", "prod"], var.environment)
    error_message = "Environment must be dev, staging, or prod."
  }
}

variable "vpc_cidr" {
  description = "CIDR block for the VPC"
  type        = string
  default     = "10.0.0.0/16"
}

variable "cluster_name" {
  description = "EKS cluster name prefix"
  type        = string
  default     = "fxops"
}

variable "eks_cluster_version" {
  description = "Kubernetes version for EKS"
  type        = string
  default     = "1.30"
}

# Node group sizing
variable "services_node_min" {
  description = "Minimum nodes in services node group"
  type        = number
  default     = 3
}

variable "services_node_max" {
  description = "Maximum nodes in services node group"
  type        = number
  default     = 10
}

variable "agents_node_min" {
  description = "Minimum nodes in agents node group"
  type        = number
  default     = 1
}

variable "agents_node_max" {
  description = "Maximum nodes in agents node group"
  type        = number
  default     = 4
}

# RDS Configuration
variable "rds_instance_class" {
  description = "RDS instance class"
  type        = string
  default     = "db.t4g.medium"
}

variable "rds_multi_az" {
  description = "Enable Multi-AZ for RDS"
  type        = bool
  default     = false
}

variable "rds_storage_type" {
  description = "Storage type for RDS (gp3 or io2)"
  type        = string
  default     = "gp3"
}

# MSK Configuration
variable "msk_instance_type" {
  description = "MSK broker instance type"
  type        = string
  default     = "kafka.t3.small"
}

variable "msk_broker_count" {
  description = "Number of MSK broker nodes"
  type        = number
  default     = 2
}

# ElastiCache Configuration
variable "redis_node_type" {
  description = "ElastiCache Redis node type"
  type        = string
  default     = "cache.t4g.micro"
}

variable "redis_num_node_groups" {
  description = "Number of Redis shard groups"
  type        = number
  default     = 1
}

variable "redis_replicas_per_group" {
  description = "Replicas per shard group"
  type        = number
  default     = 0
}

# DocumentDB Configuration
variable "docdb_instance_class" {
  description = "DocumentDB instance class"
  type        = string
  default     = "db.t4g.medium"
}

variable "docdb_replica_count" {
  description = "Number of DocumentDB read replicas"
  type        = number
  default     = 0
}

# Neptune Configuration
variable "neptune_instance_class" {
  description = "Neptune instance class"
  type        = string
  default     = "db.t4g.medium"
}

variable "neptune_replica_count" {
  description = "Number of Neptune read replicas"
  type        = number
  default     = 0
}

# OpenSearch Configuration
variable "opensearch_instance_type" {
  description = "OpenSearch data node instance type"
  type        = string
  default     = "t3.medium.search"
}

variable "opensearch_instance_count" {
  description = "Number of OpenSearch data nodes"
  type        = number
  default     = 1
}

variable "opensearch_ebs_volume_size" {
  description = "EBS volume size in GB per OpenSearch node"
  type        = number
  default     = 50
}

# Domain / Certificate
variable "domain_name" {
  description = "Base domain name for the platform (placeholder)"
  type        = string
  default     = "fxops.example.com"
}
