# =============================================================================
# FXOps Platform — VPC, Subnets, NAT Gateways, Security Groups
# =============================================================================

locals {
  azs              = slice(data.aws_availability_zones.available.names, 0, 3)
  public_cidrs     = ["10.0.1.0/24", "10.0.2.0/24", "10.0.3.0/24"]
  private_cidrs    = ["10.0.10.0/24", "10.0.11.0/24", "10.0.12.0/24"]
  data_cidrs       = ["10.0.20.0/24", "10.0.21.0/24", "10.0.22.0/24"]
  cluster_fullname = "${var.cluster_name}-${var.environment}"
}

# --- VPC ---
resource "aws_vpc" "main" {
  cidr_block           = var.vpc_cidr
  enable_dns_hostnames = true
  enable_dns_support   = true

  tags = { Name = "fxops-${var.environment}" }
}

resource "aws_internet_gateway" "main" {
  vpc_id = aws_vpc.main.id
  tags   = { Name = "fxops-igw-${var.environment}" }
}

# --- Public Subnets (ALB, NAT GW) ---
resource "aws_subnet" "public" {
  count                   = 3
  vpc_id                  = aws_vpc.main.id
  cidr_block              = local.public_cidrs[count.index]
  availability_zone       = local.azs[count.index]
  map_public_ip_on_launch = true

  tags = {
    Name                                              = "fxops-public-${local.azs[count.index]}"
    "kubernetes.io/role/elb"                          = "1"
    "kubernetes.io/cluster/${local.cluster_fullname}" = "shared"
  }
}

# --- Private Subnets (EKS nodes) ---
resource "aws_subnet" "private" {
  count             = 3
  vpc_id            = aws_vpc.main.id
  cidr_block        = local.private_cidrs[count.index]
  availability_zone = local.azs[count.index]

  tags = {
    Name                                              = "fxops-private-${local.azs[count.index]}"
    "kubernetes.io/role/internal-elb"                 = "1"
    "kubernetes.io/cluster/${local.cluster_fullname}" = "shared"
    "karpenter.sh/discovery"                          = local.cluster_fullname
  }
}

# --- Data Subnets (RDS, MSK, ElastiCache, DocumentDB, Neptune, OpenSearch) ---
resource "aws_subnet" "data" {
  count             = 3
  vpc_id            = aws_vpc.main.id
  cidr_block        = local.data_cidrs[count.index]
  availability_zone = local.azs[count.index]

  tags = { Name = "fxops-data-${local.azs[count.index]}" }
}

# --- NAT Gateways (one per AZ for HA) ---
resource "aws_eip" "nat" {
  count  = 3
  domain = "vpc"
  tags   = { Name = "fxops-nat-eip-${local.azs[count.index]}" }
}

resource "aws_nat_gateway" "main" {
  count         = 3
  allocation_id = aws_eip.nat[count.index].id
  subnet_id     = aws_subnet.public[count.index].id
  tags          = { Name = "fxops-nat-${local.azs[count.index]}" }
}

# --- Route Tables ---
resource "aws_route_table" "public" {
  vpc_id = aws_vpc.main.id
  route {
    cidr_block = "0.0.0.0/0"
    gateway_id = aws_internet_gateway.main.id
  }
  tags = { Name = "fxops-public-rt" }
}

resource "aws_route_table_association" "public" {
  count          = 3
  subnet_id      = aws_subnet.public[count.index].id
  route_table_id = aws_route_table.public.id
}

resource "aws_route_table" "private" {
  count  = 3
  vpc_id = aws_vpc.main.id
  route {
    cidr_block     = "0.0.0.0/0"
    nat_gateway_id = aws_nat_gateway.main[count.index].id
  }
  tags = { Name = "fxops-private-rt-${local.azs[count.index]}" }
}

resource "aws_route_table_association" "private" {
  count          = 3
  subnet_id      = aws_subnet.private[count.index].id
  route_table_id = aws_route_table.private[count.index].id
}

resource "aws_route_table_association" "data" {
  count          = 3
  subnet_id      = aws_subnet.data[count.index].id
  route_table_id = aws_route_table.private[count.index].id
}

# --- Security Groups ---
resource "aws_security_group" "eks_services" {
  name_prefix = "fxops-eks-services-"
  description = "Security group for EKS service pods"
  vpc_id      = aws_vpc.main.id

  egress {
    from_port   = 0
    to_port     = 0
    protocol    = "-1"
    cidr_blocks = ["0.0.0.0/0"]
  }

  tags = { Name = "fxops-eks-services-${var.environment}" }
}

resource "aws_security_group" "eks_agents" {
  name_prefix = "fxops-eks-agents-"
  description = "Security group for n8n agent pods"
  vpc_id      = aws_vpc.main.id

  egress {
    from_port   = 0
    to_port     = 0
    protocol    = "-1"
    cidr_blocks = ["0.0.0.0/0"]
  }

  tags = { Name = "fxops-eks-agents-${var.environment}" }
}

resource "aws_security_group" "eks_infra" {
  name_prefix = "fxops-eks-infra-"
  description = "Security group for infra pods (Fluent Bit, operators)"
  vpc_id      = aws_vpc.main.id

  egress {
    from_port   = 0
    to_port     = 0
    protocol    = "-1"
    cidr_blocks = ["0.0.0.0/0"]
  }

  tags = { Name = "fxops-eks-infra-${var.environment}" }
}
