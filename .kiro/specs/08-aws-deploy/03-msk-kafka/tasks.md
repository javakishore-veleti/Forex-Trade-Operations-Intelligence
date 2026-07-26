# Tasks — MSK Kafka (Event Stream on AWS)

> **Stage 3 of 3** (`requirements.md → design.md → tasks.md`). Execute top-to-bottom;
> each task is atomic and independently verifiable. Mark `[x]` as completed.

## 0. Scaffold

- [ ] 0.1 Create `DevOps/AWS/terraform/modules/msk/` with `main.tf`, `security.tf`, `glue.tf`, `monitoring.tf`, `variables.tf`, `outputs.tf`. (§9)
- [ ] 0.2 Add module reference in `DevOps/AWS/terraform/environments/dev/main.tf` and `prod/main.tf`.
- [ ] 0.3 Create `DevOps/AWS/helm/platform/msk-topic-provisioner/` directory.

## 1. MSK Cluster (Req 1)

- [ ] 1.1 Define `aws_msk_cluster` resource with cluster name `fxops-<env>`, Kafka version `3.6.0`. (§2)
- [ ] 1.2 Set `number_of_broker_nodes`: 3 for prod, 2 for dev. (Req 1.1, Req 7.1)
- [ ] 1.3 Configure broker node group: instance type `kafka.m7g.large` (prod) / `kafka.t3.small` (dev). (Req 1.2)
- [ ] 1.4 Set EBS volume size 100 GB with provisioned throughput for prod. (Req 7.2)
- [ ] 1.5 Place brokers in data subnets only. (Req 1.4)
- [ ] 1.6 Enable tiered storage. (Req 1.5, Req 6.4) **Verify:** `terraform validate` passes.

## 2. Broker Configuration (Req 1)

- [ ] 2.1 Define `aws_msk_configuration` with server properties. (§3)
- [ ] 2.2 Set `auto.create.topics.enable=false`. (Req 2.3)
- [ ] 2.3 Set `default.replication.factor=3`, `min.insync.replicas=2`. (Req 1.1)
- [ ] 2.4 Set `num.partitions=6`, `unclean.leader.election.enable=false`.
- [ ] 2.5 Set `log.retention.hours=720` (30 days default). **Verify:** configuration referenced by cluster.

## 3. Encryption and Authentication (Req 3)

- [ ] 3.1 Set `encryption_in_transit.client_broker = "TLS"`, `in_cluster = true`. (Req 3.1, 3.2)
- [ ] 3.2 Set `encryption_at_rest_kms_key_arn` to CMK variable. (Req 3)
- [ ] 3.3 Enable `client_authentication.sasl.iam = true`, disable unauthenticated. (Req 3.3)
- [ ] 3.4 Verify no plaintext listener is configured. **Verify:** `terraform plan` shows TLS-only.

## 4. Security Groups (Req 3.5)

- [ ] 4.1 Define MSK security group allowing ingress on port 9098 from EKS services SG. (§4)
- [ ] 4.2 No `0.0.0.0/0` ingress rules; private access only. (Req 3.5)
- [ ] 4.3 Allow inter-broker communication (self-referencing SG rule). **Verify:** no public ingress in plan.

## 5. Topic Provisioning (Req 2)

- [ ] 5.1 Create `DevOps/AWS/scripts/provision-topics-msk.py` reading `topic-registry.yml` and creating topics via Kafka AdminClient with IAM auth. (Req 2.1, 2.3)
- [ ] 5.2 Containerize as `fxops-topic-provisioner` with Dockerfile. (§5)
- [ ] 5.3 Create Helm Job manifest `msk-topic-provisioner/job.yaml` with IRSA service account. (§5)
- [ ] 5.4 Create ConfigMap from `topic-registry.yml` for the job. (Req 2.1)
- [ ] 5.5 Verify idempotency: re-running job does not fail or alter existing topics. **Verify:** job completes with exit 0.

## 6. Glue Schema Registry (Req 4)

- [ ] 6.1 Define `aws_glue_registry` resource for `fxops-events-<env>`. (§6)
- [ ] 6.2 Define `aws_glue_schema` for each domain topic value schema (7 schemas). (Req 4.3)
- [ ] 6.3 Set compatibility to `BACKWARD` for all schemas. (Req 4.2)
- [ ] 6.4 Copy schema JSON files from `DevOps/Local/EVENT_STREAM/schema-registry/subjects/` into Terraform module. (Req 4.1) **Verify:** `terraform plan` shows registry + 7 schemas.

## 7. IAM Policies per Service (Req 3.4)

- [ ] 7.1 Create IAM policy document scoping `kafka-cluster:WriteData` to declared produce topics per service. (§7)
- [ ] 7.2 Create IAM policy document scoping `kafka-cluster:ReadData` to declared consume topics per service.
- [ ] 7.3 Attach policies to IRSA roles created in EKS cluster module. **Verify:** each service role has topic-scoped access.

## 8. Monitoring and Alerting (Req 5)

- [ ] 8.1 Create CloudWatch log group for MSK broker logs. (§2)
- [ ] 8.2 Enable open monitoring (JMX + Node exporter). (Req 5.1)
- [ ] 8.3 Define CloudWatch alarms: `UnderReplicatedPartitions > 0`, `ConsumerLag > 10000`, `DiskUsed > 80%`. (Req 5.4)
- [ ] 8.4 Configure Prometheus scrape target in Grafana for JMX metrics. **Verify:** alarms present in `terraform plan`.

## 9. Service Helm Integration

- [ ] 9.1 Output `bootstrap_brokers_sasl_iam` from MSK module.
- [ ] 9.2 Update service Helm values with MSK connection properties (§8).
- [ ] 9.3 Add MSK IAM auth library (`aws-msk-iam-auth`) as a dependency note in service POMs. **Verify:** `terraform output` shows bootstrap broker endpoint.
