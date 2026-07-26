# ADR-0036: Terraform over CloudFormation for Infrastructure-as-Code

## Status
Accepted

## Context
The platform targets two cloud providers (AWS and Azure) and needs infrastructure-as-code (IaC) to provision EKS/AKS clusters, managed databases, messaging, caching, and observability infrastructure. The IaC tool choice affects developer experience, CI/CD integration, drift detection, and the ability to share patterns across clouds.

## Decision
Use **Terraform** (HashiCorp Configuration Language) as the sole IaC tool for both AWS and Azure deployments.

- AWS infrastructure lives in `DevOps/AWS/terraform/`
- Azure infrastructure (future) will live in `DevOps/Azure/terraform/`
- State stored in S3+DynamoDB (AWS) or Azure Blob+Table (Azure)
- Modules shared where possible (e.g. Helm release patterns)

## Alternatives Considered

### AWS CloudFormation
- **Pros**: Native AWS integration, no state file to manage, automatic rollback on failure, drift detection built-in, StackSets for multi-account, no third-party dependency
- **Cons**: AWS-only (cannot target Azure), YAML/JSON verbosity, slower iteration cycle (create-stack takes minutes), no `plan` equivalent (change sets are limited), poor module reuse across clouds
- **Rejected because**: This repo targets both AWS and Azure; maintaining CloudFormation for AWS and ARM/Bicep for Azure doubles the IaC learning curve and prevents pattern sharing

### AWS CDK (Cloud Development Kit)
- **Pros**: TypeScript/Python, type-safe constructs, compiles to CloudFormation, AWS-native
- **Cons**: AWS-only (CDKTF exists but is less mature), adds a compilation step, CloudFormation limits still apply underneath, large dependency tree
- **Rejected because**: Still AWS-only at its core; CDKTF adds complexity without clear benefit over native Terraform HCL

### Pulumi
- **Pros**: Real programming languages (TypeScript, Python, Go), multi-cloud, state management similar to Terraform
- **Cons**: Smaller ecosystem than Terraform, fewer community modules, Pulumi Cloud dependency for some features, less industry adoption for financial services
- **Rejected because**: Terraform has broader adoption, more community modules for AWS/Azure services, and better hiring pool familiarity

### ARM Templates / Bicep (Azure-only)
- **Pros**: Native Azure integration, first-class support for all Azure resources
- **Cons**: Azure-only, cannot target AWS
- **Rejected because**: Same reason as CloudFormation — single-cloud lock-in when the project targets both

## Consequences
- **Positive**: One IaC language for both clouds; `terraform plan` gives explicit preview before apply; large module ecosystem; team learns one tool
- **Positive**: State file makes infrastructure queryable (`terraform show`, `terraform output`)
- **Negative**: Must manage state backend (S3 bucket + DynamoDB table for locking)
- **Negative**: No automatic rollback on failure (must handle manually or via CI/CD pipeline logic)
- **Negative**: Drift detection requires `terraform plan` runs (not automatic like CloudFormation)
- **Mitigation**: CI/CD pipeline runs `terraform plan` on every PR and scheduled drift detection weekly
