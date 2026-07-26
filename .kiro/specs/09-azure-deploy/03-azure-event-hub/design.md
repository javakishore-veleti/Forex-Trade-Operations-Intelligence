# Design Document — Azure Event Hub (Event Stream on Azure)

> **Stage 2 of 3** (`requirements.md → design.md → tasks.md`). Resolves `CloudTargetBinding`
> for `EVENT_STREAM` → Azure Event Hub (Kafka-compatible). Concrete Bicep configuration.

## 1. Overview

The platform `EVENT_STREAM` maps to **Azure Event Hub** with Kafka protocol support. Services
connect using standard Apache Kafka client libraries (Spring Kafka) over SASL_SSL on port 9093.
The namespace is Premium tier for production (auto-scale Processing Units, ≥ 20 consumer groups).

**Technology Bindings:**

| Technology Role | Azure Service | Configuration |
|---|---|---|
| `EVENT_STREAM` | Azure Event Hub (Kafka surface) | Premium tier, zone-redundant |
| Schema Registry | Azure Schema Registry (Event Hub) | Avro/JSON, BACKWARD compat |
| Identity access | Entra ID (Azure AD) RBAC | Managed Identity OAUTHBEARER |
| Network | Private Endpoint | No public access |

## 2. Namespace Configuration

```bicep
// DevOps/Azure/bicep/modules/eventhub/main.bicep (conceptual)
resource ehNamespace 'Microsoft.EventHub/namespaces@2024-01-01' = {
  name: 'fxops-eh-${environment}'
  location: location
  sku: {
    name: environment == 'prod' ? 'Premium' : 'Standard'
    tier: environment == 'prod' ? 'Premium' : 'Standard'
    capacity: environment == 'prod' ? 1 : 1  // PU for Premium, TU for Standard
  }
  properties: {
    isAutoInflateEnabled: environment != 'prod'
    maximumThroughputUnits: environment != 'prod' ? 4 : 0
    zoneRedundant: true
    kafkaEnabled: true
    minimumTlsVersion: '1.2'
    publicNetworkAccess: 'Disabled'
  }
}
```

## 3. Event Hub (Topic) Instances

Mapped from the Kafka topic registry (`03-events/01-kafka-topic-design`):

| Event Hub Name | Partitions | Retention (days) | Consumer Groups |
|---|---|---|---|
| `trade.captured` | 12 | 7 | trade-lifecycle-cg, risk-calculation-cg, event-sequence-cg |
| `trade.lifecycle` | 12 | 7 | eod-processing-cg, state-reconciliation-cg, event-sequence-cg |
| `risk.calculated` | 8 | 7 | trade-lifecycle-cg, eod-processing-cg |
| `eod.status` | 4 | 7 | state-reconciliation-cg |
| `trade.dlq` | 4 | 14 | dlq-triage-cg |
| *(remaining topics per registry)* | per design | 7 | per consuming service |

```bicep
resource eventHub 'Microsoft.EventHub/namespaces/eventhubs@2024-01-01' = {
  name: 'trade.captured'
  parent: ehNamespace
  properties: {
    partitionCount: 12
    messageRetentionInDays: environment == 'prod' ? 7 : 1
  }
}

resource consumerGroup 'Microsoft.EventHub/namespaces/eventhubs/consumergroups@2024-01-01' = {
  name: 'trade-lifecycle-cg'
  parent: eventHub
}
```

## 4. Kafka Protocol Connection (Spring Boot)

```yaml
# application-azure.yml (profile activated in AKS)
spring:
  kafka:
    bootstrap-servers: fxops-eh-${ENV}.servicebus.windows.net:9093
    properties:
      security.protocol: SASL_SSL
      sasl.mechanism: OAUTHBEARER
      sasl.jaas.config: >
        org.apache.kafka.common.security.oauthbearer.OAuthBearerLoginModule required;
      sasl.login.callback.handler.class: com.fxops.shared.kafka.AzureOAuthCallbackHandler
    producer:
      acks: all
      key-serializer: org.apache.kafka.common.serialization.StringSerializer
      value-serializer: org.springframework.kafka.support.serializer.JsonSerializer
    consumer:
      auto-offset-reset: earliest
      enable-auto-commit: false
```

Custom `AzureOAuthCallbackHandler` obtains Azure AD token via `azure-identity` SDK using Workload Identity.

## 5. Kafka Streams Compatibility

- Kafka Streams works with Event Hub Premium tier (required for Kafka Streams API).
- `application.id` maps to an Event Hub consumer group.
- State stores use changelog topics (auto-created, must be pre-provisioned on Standard tier).
- Event-sequence-processor validated for: windowed aggregation, stateful joins, punctuators.

## 6. Schema Registry

```bicep
resource schemaGroup 'Microsoft.EventHub/namespaces/schemagroups@2024-01-01' = {
  name: 'fxops-events'
  parent: ehNamespace
  properties: {
    schemaCompatibility: 'Backward'
    schemaType: 'Json'
    groupProperties: {}
  }
}
```

Alternative: schema contracts enforced in code via `shared-domain-contracts` (current approach for JSON serialization).

## 7. Private Endpoint

```bicep
resource privateEndpoint 'Microsoft.Network/privateEndpoints@2023-09-01' = {
  name: 'fxops-eh-pe-${environment}'
  location: location
  properties: {
    subnet: { id: dataSubnet.id }
    privateLinkServiceConnections: [{
      name: 'eh-connection'
      properties: {
        privateLinkServiceId: ehNamespace.id
        groupIds: ['namespace']
      }
    }]
  }
}
```

Private DNS zone: `privatelink.servicebus.windows.net` linked to VNet.

## 8. RBAC Assignments

| Identity | Role | Scope |
|---|---|---|
| `fxops-trade-ingest-<env>` | Azure Event Hubs Data Sender | `trade.captured` |
| `fxops-trade-lifecycle-<env>` | Azure Event Hubs Data Sender + Receiver | namespace |
| `fxops-risk-calculation-<env>` | Azure Event Hubs Data Sender + Receiver | namespace |
| `fxops-eod-processing-<env>` | Azure Event Hubs Data Sender + Receiver | namespace |
| `fxops-event-sequence-<env>` | Azure Event Hubs Data Sender + Receiver + Owner | namespace (Streams) |

## 9. Bicep Module Layout

```
DevOps/Azure/bicep/modules/eventhub/
├── main.bicep           ← namespace + network config
├── topics.bicep         ← Event Hub instances + consumer groups
├── schema-registry.bicep
├── private-endpoint.bicep
└── rbac.bicep           ← role assignments
```
