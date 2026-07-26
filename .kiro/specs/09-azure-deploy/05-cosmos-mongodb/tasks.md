# Tasks — Cosmos DB MongoDB API (Document Store on Azure)

> **Stage 3 of 3** (`requirements.md → design.md → tasks.md`). Execute top-to-bottom;
> each task is atomic and independently verifiable. Mark `[x]` as completed.

## 0. Scaffold

- [ ] 0.1 Create `DevOps/Azure/bicep/modules/cosmos/` with `main.bicep`, `database.bicep`, `collections.bicep`, `private-endpoint.bicep`, `rbac.bicep`. (§9)

## 1. Cosmos DB Account (Req 1)

- [ ] 1.1 Define Cosmos DB account resource: MongoDB API, server version 7.0.
- [ ] 1.2 Set consistency policy to `Session` (read-your-own-writes).
- [ ] 1.3 Enable availability zones for zone-redundant replication.
- [ ] 1.4 Enable geo-redundancy for prod, disable for dev.
- [ ] 1.5 Disable public network access.
- [ ] 1.6 Enable automatic failover.
- [ ] 1.7 Configure continuous backup (PITR) with 7-day window.
- [ ] 1.8 Output account endpoint, connection string. **Verify:** `az bicep build` succeeds.

## 2. Database and Collections (Req 2, 3)

- [ ] 2.1 Define `fxops` database with shared throughput for dev (400 RU/s manual).
- [ ] 2.2 Define `trade_audit_history` collection: partition key `/tradeId`, autoscale max 4000 RU/s (prod).
- [ ] 2.3 Define `lifecycle_events` collection: partition key `/tradeId`, autoscale max 4000 RU/s.
- [ ] 2.4 Define `counterparties` collection: partition key `/counterpartyId`, autoscale max 1000 RU/s.
- [ ] 2.5 Define `trading_books` collection: partition key `/bookId`, autoscale max 1000 RU/s.
- [ ] 2.6 Define `reconciliation_snapshots` collection: partition key `/tradeId`, TTL 90 days, 2000 RU/s.
- [ ] 2.7 Define `agent_context` collection: partition key `/sessionId`, TTL 7 days, 1000 RU/s.
- [ ] 2.8 Define indexes per §3 table (compound indexes for query patterns). **Verify:** collections + indexes in Bicep.

## 3. Private Endpoint (Req 6)

- [ ] 3.1 Create Private Endpoint in data subnet targeting Cosmos DB account (groupId: MongoDB).
- [ ] 3.2 Create Private DNS Zone `privatelink.mongo.cosmos.azure.com` linked to VNet.
- [ ] 3.3 Create DNS A record. **Verify:** private endpoint in Bicep.

## 4. Connection String Migration (Req 4)

- [ ] 4.1 Create `application-azure.yml` snippet with Cosmos DB MongoDB connection string (port 10255, ssl=true, retrywrites=false).
- [ ] 4.2 Store connection string in Key Vault.
- [ ] 4.3 Document differences from local MongoDB (retrywrites, port, SSL).
- [ ] 4.4 Document unsupported features and mitigations per §6 compatibility table.
- [ ] 4.5 Validate Spring Data MongoDB operations against Cosmos DB (change streams, aggregation). **Verify:** valid Spring Boot YAML.

## 5. Indexing (Req 5)

- [ ] 5.1 Define explicit indexes for `trade_audit_history`: `{tradeId, timestamp}`, `{region, bookingDate}`.
- [ ] 5.2 Define indexes for `lifecycle_events`: `{tradeId, eventType}`.
- [ ] 5.3 Document no wildcard indexes (RU cost control).
- [ ] 5.4 Create index provisioning script (run at collection creation time). **Verify:** indexes in Bicep collection definition.

## 6. Security (Req 6)

- [ ] 6.1 Enable Entra ID RBAC (where supported) for pod access.
- [ ] 6.2 Store primary/secondary keys in Key Vault.
- [ ] 6.3 Enable diagnostic settings (data plane + control plane) → Log Analytics.
- [ ] 6.4 Confirm TLS 1.2 minimum on all connections. **Verify:** diagnostic settings in Bicep.

## 7. Monitoring (Req 3, 5)

- [ ] 7.1 Create alert rule: `Http429` (throttling) > 10/min → scale up RU/s.
- [ ] 7.2 Monitor `TotalRequestUnits`, `DocumentCount`, `DataUsage` metrics.
- [ ] 7.3 Set up cost alert when monthly RU consumption exceeds budget threshold. **Verify:** alerts in Bicep.

## 8. Cost Controls (Req 7)

- [ ] 8.1 Define dev parameters: serverless or minimum manual throughput (400 RU/s).
- [ ] 8.2 Define prod parameters: autoscale with defined max per collection.
- [ ] 8.3 Configure TTL on transient collections to auto-expire data.
- [ ] 8.4 Tag all resources with `project:fxops`, `environment:<env>`. **Verify:** tags in Bicep.
