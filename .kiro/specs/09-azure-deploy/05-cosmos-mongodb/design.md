# Design Document — Cosmos DB MongoDB API (Document Store on Azure)

> **Stage 2 of 3** (`requirements.md → design.md → tasks.md`). Resolves `CloudTargetBinding`
> for `DOCUMENT_STORE` → Azure Cosmos DB for MongoDB. Concrete Bicep configuration.

## 1. Overview

The platform `DOCUMENT_STORE` maps to **Azure Cosmos DB for MongoDB** (RU-based API). Services
connect using the standard MongoDB connection string format; Spring Data MongoDB requires only
a URI change. Partition keys are chosen for trade-scoped access patterns.

**Technology Bindings:**

| Technology Role | Azure Service | Configuration |
|---|---|---|
| `DOCUMENT_STORE` | Azure Cosmos DB for MongoDB | API version 7.0, RU-based |
| Client | Spring Data MongoDB (MongoClient) | Standard connection string |
| Identity access | Entra ID RBAC + resource tokens | Managed Identity |
| Network | Private Endpoint | No public access |
| Encryption | Azure-managed or CMK via Key Vault | At rest |

## 2. Account Configuration

```bicep
// DevOps/Azure/bicep/modules/cosmos/main.bicep (conceptual)
resource cosmosAccount 'Microsoft.DocumentDB/databaseAccounts@2024-02-15-preview' = {
  name: 'fxops-cosmos-${environment}'
  location: location
  kind: 'MongoDB'
  properties: {
    databaseAccountOfferType: 'Standard'
    apiProperties: { serverVersion: '7.0' }
    locations: [
      { locationName: location, failoverPriority: 0, isZoneRedundant: true }
    ]
    consistencyPolicy: { defaultConsistencyLevel: 'Session' }
    publicNetworkAccess: 'Disabled'
    enableAutomaticFailover: true
    capabilities: [
      { name: 'EnableMongo' }
      { name: 'EnableServerless' }   // dev only; prod uses provisioned
    ]
    backupPolicy: {
      type: 'Continuous'
      continuousModeProperties: { tier: 'Continuous7Days' }
    }
  }
}
```

- **Session consistency** — sufficient for per-user/per-request read-your-own-writes.
- **Continuous backup** — PITR with 7-day (or 30-day for prod) window.

## 3. Database and Collections

```bicep
resource database 'Microsoft.DocumentDB/databaseAccounts/mongodbDatabases@2024-02-15-preview' = {
  name: 'fxops'
  parent: cosmosAccount
  properties: {
    resource: { id: 'fxops' }
    options: { throughput: environment == 'prod' ? null : 400 }  // manual for dev
  }
}
```

| Collection | Partition Key | Autoscale Max RU/s (prod) | TTL |
|---|---|---|---|
| `trade_audit_history` | `/tradeId` | 4000 | — |
| `lifecycle_events` | `/tradeId` | 4000 | — |
| `counterparties` | `/counterpartyId` | 1000 | — |
| `trading_books` | `/bookId` | 1000 | — |
| `reconciliation_snapshots` | `/tradeId` | 2000 | 90 days |
| `agent_context` | `/sessionId` | 1000 | 7 days |

```bicep
resource collection 'Microsoft.DocumentDB/databaseAccounts/mongodbDatabases/collections@2024-02-15-preview' = {
  name: 'trade_audit_history'
  parent: database
  properties: {
    resource: {
      id: 'trade_audit_history'
      shardKey: { tradeId: 'Hash' }
      indexes: [
        { key: { keys: ['_id'] } }
        { key: { keys: ['tradeId', 'timestamp'] } }
        { key: { keys: ['region', 'bookingDate'] } }
      ]
    }
    options: {
      autoscaleSettings: { maxThroughput: 4000 }
    }
  }
}
```

## 4. Partition Key Strategy Detail

- **`tradeId`** for trade-scoped collections: all reads/writes for a trade hit one logical partition → no cross-partition queries for primary access.
- **Natural key** for reference collections: `counterpartyId`, `bookId` — even distribution, point-reads only.
- **`sessionId`** for agent context: short-lived, auto-expired via TTL.
- Logical partition limit: 20 GB. Trade audit growth bounded by trade count × events/trade (well within limit for synthetic data).

## 5. Connection String Migration

```yaml
# application-azure.yml
spring:
  data:
    mongodb:
      uri: mongodb://fxops-cosmos-${ENV}:${COSMOS_KEY}@fxops-cosmos-${ENV}.mongo.cosmos.azure.com:10255/fxops?ssl=true&retrywrites=false&maxIdleTimeMS=120000
```

Key differences from local MongoDB:
- Port: 10255 (Cosmos) vs 27017 (local)
- `retrywrites=false` — Cosmos MongoDB API does not support retryable writes on RU model
- `ssl=true` mandatory
- Connection string from Key Vault

## 6. Compatibility Considerations

| Feature | Local MongoDB | Cosmos DB MongoDB API | Mitigation |
|---|---|---|---|
| Multi-doc transactions | Supported | Limited (single-partition) | Design for single-partition writes |
| `$graphLookup` | Supported | Not supported | Not used (graph queries go to `GRAPH_STORE`) |
| Retryable writes | Supported | Not on RU-based | `retrywrites=false` in URI |
| Change streams | Supported | Supported | Works for event-driven reads |
| Aggregation pipeline | Full | Most stages supported | Validate complex pipelines |

## 7. Private Endpoint

```bicep
resource privateEndpoint 'Microsoft.Network/privateEndpoints@2023-09-01' = {
  name: 'fxops-cosmos-pe-${environment}'
  location: location
  properties: {
    subnet: { id: dataSubnet.id }
    privateLinkServiceConnections: [{
      name: 'cosmos-connection'
      properties: {
        privateLinkServiceId: cosmosAccount.id
        groupIds: ['MongoDB']
      }
    }]
  }
}
```

Private DNS zone: `privatelink.mongo.cosmos.azure.com` linked to VNet.

## 8. Monitoring

- Azure Monitor metrics: `TotalRequests`, `TotalRequestUnits`, `Http429`, `DocumentCount`.
- Alert: `Http429` (throttling) > 10/min → scale up RU/s.
- Diagnostic settings → Log Analytics workspace.

## 9. Bicep Module Layout

```
DevOps/Azure/bicep/modules/cosmos/
├── main.bicep              ← account + consistency + backup
├── database.bicep          ← database + throughput
├── collections.bicep       ← collections + indexes + partition keys
├── private-endpoint.bicep
└── rbac.bicep
```
