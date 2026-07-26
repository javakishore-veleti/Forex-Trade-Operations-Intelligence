# =============================================================================
# FXOps Platform — AWS Infrastructure (Terraform Root)
# Synthetic data only — all identifiers use FX- prefix
# =============================================================================

terraform {
  required_version = ">= 1.6.0"

  required_providers {
    aws = {
      source  = "hashicorp/aws"
      version = "~> 5.0"
    }
    random = {
      source  = "hashicorp/random"
      version = "~> 3.6"
    }
    tls = {
      source  = "hashicorp/tls"
      version = "~> 4.0"
    }
  }

  backend "s3" {
    bucket         = "fxops-terraform-state-123456789012"
    key            = "infrastructure/terraform.tfstate"
    region         = "us-east-1"
    dynamodb_table = "fxops-terraform-locks"
    encrypt        = true
  }
}

provider "aws" {
  region = var.aws_region

  default_tags {
    tags = {
      project     = "fxops"
      environment = var.environment
      managed-by  = "terraform"
    }
  }
}

# -----------------------------------------------------------------------------
# Data Sources
# -----------------------------------------------------------------------------

data "aws_caller_identity" "current" {}

data "aws_region" "current" {}

data "aws_availability_zones" "available" {
  state = "available"
  filter {
    name   = "opt-in-status"
    values = ["opt-in-not-required"]
  }
}

data "aws_partition" "current" {}

# KMS key for envelope encryption across all data stores
resource "aws_kms_key" "fxops" {
  description             = "FXOps platform encryption key - ${var.environment}"
  deletion_window_in_days = 30
  enable_key_rotation     = true

  tags = {
    service = "platform-encryption"
  }
}

resource "aws_kms_alias" "fxops" {
  name          = "alias/fxops-${var.environment}"
  target_key_id = aws_kms_key.fxops.key_id
}

# CloudWatch log group for EKS control plane
resource "aws_cloudwatch_log_group" "eks" {
  name              = "/aws/eks/fxops-${var.environment}/cluster"
  retention_in_days = 90

  tags = {
    service = "eks"
  }
}
