# Tasks — Azure Event Hub (Event Stream on Azure)

> **Stage 3 of 3** (`requirements.md → design.md → tasks.md`). Execute top-to-bottom;
> each task is atomic and independently verifiable. Mark `[x]` as completed.

## 0. Scaffold

- [ ] 0.1 Create `DevOps/Azure/bicep/modules/eventhub/` with `main.bicep`, `topics.bicep`, `private-endpoint.bicep`, `rbac.bicep`. (§9)
- [ ] 0.2 Create `DevOps/Azure/bicep/modules/eventhub/schema-registry.bicep`. (§6)

## 1. Namespace (Req 1)

- [ ] 1.1 Define Event Hub Namespace resource: Premium tier (prod) / Standard tier (dev).
- [ ] 1.2 Enable Kafka protocol (`kafkaEnabled: true`).
- [ ] 1.3 Enable zone redundancy.
- [ ] 1.4 Set minimum TLS version to 1.2.
- [ ] 1.5 Disable public network access.
- [ ] 1.6 Configure auto-inflate for Standard tier (max 4 TU); auto-scale for Premium.
- [ ] 1.7 Output namespace FQDN (bootstrap servers endpoint). **Verify:** `az bicep build` succeeds.

## 2. Event Hub Instances / Topics (Req 2)

- [ ] 2.1 Define `trade.captured` Event Hub: 12 partitions, 7-day retention (prod) / 1-day (dev).
- [ ] 2.2 Define `trade.lifecycle` Event Hub: 12 partitions.
- [ ] 2.3 Define `risk.calculated` Event Hub: 8 partitions.
- [ ] 2.4 Define `eod.status` Event Hub: 4 partitions.
- [ ] 2.5 Define `trade.dlq` Event Hub: 4 partitions, 14-day retention.
- [ ] 2.6 Define remaining Event Hub instances per topic registry (parameterized loop). **Verify:** all Event Hubs in Bicep output.

## 3. Consumer Groups (Req 3)

- [ ] 3.1 Create consumer groups per §3 table: `trade-lifecycle-cg`, `risk-calculation-cg`, `event-sequence-cg`, etc.
- [ ] 3.2 Ensure `$Default` is not used by application services (document as convention).
- [ ] 3.3 Validate consumer group count is within tier limit. **Verify:** consumer groups defined per Event Hub.

## 4. Kafka Protocol — Spring Boot Config (Req 4)

- [ ] 4.1 Create `application-azure.yml` snippet with bootstrap servers pointing to `<namespace>.servicebus.windows.net:9093`.
- [ ] 4.2 Configure `security.protocol: SASL_SSL`, `sasl.mechanism: OAUTHBEARER`.
- [ ] 4.3 Implement `AzureOAuthCallbackHandler` class using `azure-identity` SDK for token retrieval.
- [ ] 4.4 Configure producer properties: `acks=all`, JSON serializer.
- [ ] 4.5 Configure consumer properties: `auto-offset-reset=earliest`, `enable-auto-commit=false`.
- [ ] 4.6 Validate Kafka Streams compatibility with Event Hub Premium tier. **Verify:** Spring Boot app starts with Azure profile.

## 5. Schema Registry (Req 6)

- [ ] 5.1 Define Schema Group resource in namespace: `fxops-events`, JSON type, BACKWARD compatibility.
- [ ] 5.2 Document schema versioning strategy (embed version in event headers).
- [ ] 5.3 Document validation approach (shared-domain-contracts as source of truth). **Verify:** schema group in Bicep.

## 6. Private Endpoint and DNS (Req 5)

- [ ] 6.1 Create Private Endpoint in data subnet targeting namespace.
- [ ] 6.2 Create Private DNS Zone `privatelink.servicebus.windows.net` linked to VNet.
- [ ] 6.3 Create DNS A record for namespace FQDN resolving to Private Endpoint IP. **Verify:** DNS resolution test.

## 7. RBAC Assignments (Req 5)

- [ ] 7.1 Assign `Azure Event Hubs Data Sender` to trade-ingest identity on `trade.captured`.
- [ ] 7.2 Assign `Azure Event Hubs Data Sender + Receiver` to trade-lifecycle, risk-calculation, eod-processing identities on namespace.
- [ ] 7.3 Assign `Azure Event Hubs Data Owner` to event-sequence-processor identity (Kafka Streams needs management ops).
- [ ] 7.4 Restrict namespace-level shared access policies to deployment automation only. **Verify:** role assignments in Bicep.

## 8. Cost Controls (Req 7)

- [ ] 8.1 Define dev parameters: Standard tier, 1 TU, 1-day retention.
- [ ] 8.2 Define prod parameters: Premium tier, auto-scale PU, 7-day retention.
- [ ] 8.3 Tag all resources with `project:fxops`, `environment:<env>`. **Verify:** tags in Bicep output.
