# =============================================================================
# FXOps Platform — AWS Secrets Manager (Credential Management)
# All credentials stored in Secrets Manager, never in plaintext tfvars.
# =============================================================================

# --- Redis AUTH Token ---
resource "aws_secretsmanager_secret" "redis_auth" {
  name        = "fxops/redis/auth-token-${var.environment}"
  description = "ElastiCache Redis AUTH token for FXOps platform"
  kms_key_id  = aws_kms_key.fxops.arn

  tags = { service = "elasticache-redis" }
}

resource "aws_secretsmanager_secret_version" "redis_auth" {
  secret_id     = aws_secretsmanager_secret.redis_auth.id
  secret_string = random_password.redis_auth.result
}

# --- DocumentDB Master Password ---
resource "aws_secretsmanager_secret" "docdb_master" {
  name        = "fxops/docdb/master-password-${var.environment}"
  description = "DocumentDB master password for FXOps platform"
  kms_key_id  = aws_kms_key.fxops.arn

  tags = { service = "documentdb" }
}

resource "aws_secretsmanager_secret_version" "docdb_master" {
  secret_id     = aws_secretsmanager_secret.docdb_master.id
  secret_string = random_password.docdb_master.result
}

# --- OpenSearch Admin Password ---
resource "aws_secretsmanager_secret" "opensearch_admin" {
  name        = "fxops/opensearch/admin-password-${var.environment}"
  description = "OpenSearch admin password for FXOps platform"
  kms_key_id  = aws_kms_key.fxops.arn

  tags = { service = "opensearch" }
}

resource "aws_secretsmanager_secret_version" "opensearch_admin" {
  secret_id     = aws_secretsmanager_secret.opensearch_admin.id
  secret_string = random_password.opensearch_admin.result
}

# --- Rotation Configuration (90-day cycle) ---
resource "aws_secretsmanager_secret_rotation" "redis_auth" {
  secret_id           = aws_secretsmanager_secret.redis_auth.id
  rotation_lambda_arn = "arn:aws:lambda:${var.aws_region}:${data.aws_caller_identity.current.account_id}:function:fxops-secret-rotation"

  rotation_rules {
    automatically_after_days = 90
  }
}

resource "aws_secretsmanager_secret_rotation" "docdb_master" {
  secret_id           = aws_secretsmanager_secret.docdb_master.id
  rotation_lambda_arn = "arn:aws:lambda:${var.aws_region}:${data.aws_caller_identity.current.account_id}:function:fxops-secret-rotation"

  rotation_rules {
    automatically_after_days = 90
  }
}

resource "aws_secretsmanager_secret_rotation" "opensearch_admin" {
  secret_id           = aws_secretsmanager_secret.opensearch_admin.id
  rotation_lambda_arn = "arn:aws:lambda:${var.aws_region}:${data.aws_caller_identity.current.account_id}:function:fxops-secret-rotation"

  rotation_rules {
    automatically_after_days = 90
  }
}

# --- External Secrets Operator IAM Policy ---
# Grants ESO running in EKS permission to read secrets
resource "aws_iam_policy" "external_secrets" {
  name        = "fxops-external-secrets-${var.environment}"
  description = "Allow External Secrets Operator to read FXOps secrets"

  policy = jsonencode({
    Version = "2012-10-17"
    Statement = [
      {
        Effect = "Allow"
        Action = [
          "secretsmanager:GetSecretValue",
          "secretsmanager:DescribeSecret",
          "secretsmanager:ListSecrets"
        ]
        Resource = "arn:aws:secretsmanager:${var.aws_region}:${data.aws_caller_identity.current.account_id}:secret:fxops/*"
      },
      {
        Effect   = "Allow"
        Action   = ["kms:Decrypt"]
        Resource = [aws_kms_key.fxops.arn]
      }
    ]
  })

  tags = { service = "external-secrets" }
}
