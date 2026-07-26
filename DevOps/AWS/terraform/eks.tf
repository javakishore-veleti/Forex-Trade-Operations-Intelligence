# =============================================================================
# FXOps Platform — EKS Cluster, Node Groups, IRSA
# =============================================================================

# --- EKS Cluster IAM Role ---
resource "aws_iam_role" "eks_cluster" {
  name = "fxops-eks-cluster-${var.environment}"

  assume_role_policy = jsonencode({
    Version = "2012-10-17"
    Statement = [{
      Action    = "sts:AssumeRole"
      Effect    = "Allow"
      Principal = { Service = "eks.amazonaws.com" }
    }]
  })
}

resource "aws_iam_role_policy_attachment" "eks_cluster_policy" {
  policy_arn = "arn:${data.aws_partition.current.partition}:iam::aws:policy/AmazonEKSClusterPolicy"
  role       = aws_iam_role.eks_cluster.name
}

# --- EKS Cluster ---
resource "aws_eks_cluster" "fxops" {
  name     = local.cluster_fullname
  role_arn = aws_iam_role.eks_cluster.arn
  version  = var.eks_cluster_version

  vpc_config {
    subnet_ids              = aws_subnet.private[*].id
    endpoint_private_access = true
    endpoint_public_access  = false
    security_group_ids      = [aws_security_group.eks_services.id]
  }

  encryption_config {
    provider { key_arn = aws_kms_key.fxops.arn }
    resources = ["secrets"]
  }

  enabled_cluster_log_types = ["api", "audit", "authenticator", "controllerManager", "scheduler"]

  depends_on = [aws_iam_role_policy_attachment.eks_cluster_policy]

  tags = { service = "eks" }
}

# --- Node Group IAM Role ---
resource "aws_iam_role" "eks_nodes" {
  name = "fxops-eks-nodes-${var.environment}"

  assume_role_policy = jsonencode({
    Version = "2012-10-17"
    Statement = [{
      Action    = "sts:AssumeRole"
      Effect    = "Allow"
      Principal = { Service = "ec2.amazonaws.com" }
    }]
  })
}

resource "aws_iam_role_policy_attachment" "eks_worker" {
  for_each = toset([
    "arn:${data.aws_partition.current.partition}:iam::aws:policy/AmazonEKSWorkerNodePolicy",
    "arn:${data.aws_partition.current.partition}:iam::aws:policy/AmazonEKS_CNI_Policy",
    "arn:${data.aws_partition.current.partition}:iam::aws:policy/AmazonEC2ContainerRegistryReadOnly",
  ])
  policy_arn = each.value
  role       = aws_iam_role.eks_nodes.name
}

# --- Services Node Group (Graviton ARM64) ---
resource "aws_eks_node_group" "services" {
  cluster_name    = aws_eks_cluster.fxops.name
  node_group_name = "services"
  node_role_arn   = aws_iam_role.eks_nodes.arn
  subnet_ids      = aws_subnet.private[*].id
  instance_types  = ["m7g.xlarge"]
  ami_type        = "AL2023_ARM_64_STANDARD"

  scaling_config {
    desired_size = var.services_node_min
    min_size     = var.services_node_min
    max_size     = var.services_node_max
  }

  labels = { workload = "services" }

  tags = { service = "eks-services" }
}

# --- Agents Node Group ---
resource "aws_eks_node_group" "agents" {
  cluster_name    = aws_eks_cluster.fxops.name
  node_group_name = "agents"
  node_role_arn   = aws_iam_role.eks_nodes.arn
  subnet_ids      = aws_subnet.private[*].id
  instance_types  = ["r7g.large"]
  ami_type        = "AL2023_ARM_64_STANDARD"

  scaling_config {
    desired_size = var.agents_node_min
    min_size     = var.agents_node_min
    max_size     = var.agents_node_max
  }

  labels = { workload = "agents" }

  tags = { service = "eks-agents" }
}

# --- Spot Sidecars Node Group ---
resource "aws_eks_node_group" "spot_sidecars" {
  cluster_name    = aws_eks_cluster.fxops.name
  node_group_name = "spot-sidecars"
  node_role_arn   = aws_iam_role.eks_nodes.arn
  subnet_ids      = aws_subnet.private[*].id
  instance_types  = ["m7g.large"]
  capacity_type   = "SPOT"
  ami_type        = "AL2023_ARM_64_STANDARD"

  scaling_config {
    desired_size = 1
    min_size     = 1
    max_size     = 6
  }

  labels = { workload = "sidecars" }

  taint {
    key    = "spot"
    value  = "true"
    effect = "PREFER_NO_SCHEDULE"
  }

  tags = { service = "eks-sidecars" }
}

# --- OIDC Provider for IRSA ---
data "tls_certificate" "eks" {
  url = aws_eks_cluster.fxops.identity[0].oidc[0].issuer
}

resource "aws_iam_openid_connect_provider" "eks" {
  url             = aws_eks_cluster.fxops.identity[0].oidc[0].issuer
  client_id_list  = ["sts.amazonaws.com"]
  thumbprint_list = [data.tls_certificate.eks.certificates[0].sha1_fingerprint]
}

# --- IRSA Roles for Services ---
locals {
  service_accounts = {
    "trade-ingest"         = "fxops-services"
    "trade-lifecycle"      = "fxops-services"
    "risk-calculation"     = "fxops-services"
    "eod-processing"       = "fxops-services"
    "business-calendar"    = "fxops-services"
    "state-reconciliation" = "fxops-services"
    "event-sequence"       = "fxops-services"
  }
}

resource "aws_iam_role" "service_irsa" {
  for_each = local.service_accounts
  name     = "fxops-${each.key}-${var.environment}"

  assume_role_policy = jsonencode({
    Version = "2012-10-17"
    Statement = [{
      Effect    = "Allow"
      Principal = { Federated = aws_iam_openid_connect_provider.eks.arn }
      Action    = "sts:AssumeRoleWithWebIdentity"
      Condition = {
        StringEquals = {
          "${replace(aws_eks_cluster.fxops.identity[0].oidc[0].issuer, "https://", "")}:sub" = "system:serviceaccount:${each.value}:${each.key}"
          "${replace(aws_eks_cluster.fxops.identity[0].oidc[0].issuer, "https://", "")}:aud" = "sts.amazonaws.com"
        }
      }
    }]
  })

  tags = { service = each.key }
}
