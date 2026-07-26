# Design Document — Azure Cache for Redis (Cache on Azure)

> **Stage 2 of 3** (`requirements.md → design.md → tasks.md`). Resolves `CloudTargetBinding`
> for `CACHE` → Azure Cache for Redis 7.x. Concrete Bicep configuration.

## 1. Overview

The platform `CACHE` maps to **Azure Cache for Redis** at Premium tier with clustering enabled.
All services connect via the Lettuce client (Spring Boot default) over TLS (port 6380).

**Technology Bindings:**

| Technology Role | Azure Service | Configuration |
|---|---|---|
| `CACHE` | Azure Cache for Redis | 7.x, Premium tier, clustered |
| Client | Lettuce (Spring Data Redis) | TLS, cluster-aware |
| Identity access | Entra ID (Azure AD) | Managed Identity access tokens |
| Network | Private Endpoint | No public access |
| Encryption at rest | Azure-managed keys | Default |

## 2. Instance Configuration

```bicep
// DevOps/Azure/bicep/modules/redis/main.bicep (conceptual)
resource redisCache 'Microsoft.Cache/redis@2023-08-01' = {
  name: 'fxops-redis-${environment}'
  location: location
  properties: {
    sku: {
      name: environment == 'prod' ? 'Premium' : 'Basic'
      family: environment == 'prod' ? 'P' : 'C'
      capacity: environment == 'prod' ? 2 : 0   // P2 = 6GB, C0 = 250MB
    }
    enableNonSslPort: false
    minimumTlsVersion: '1.2'
    redisVersion: '7'
    publicNetworkAccess: 'Disabled'
    redisConfiguration: {
      'maxmemory-policy': 'allkeys-lru'
      'maxmemory-reserved': '125'        // MB reserved for non-cache operations
    }
  }
  zones: environment == 'prod' ? ['1', '2', '3'] : []
}
```

## 3. Clustering (Production)

```bicep
// Premium tier P2+ supports clustering
resource redisCluster 'Microsoft.Cache/redis@2023-08-01' = if (environment == 'prod') {
  name: 'fxops-redis-${environment}'
  properties: {
    shardCount: 2                         // 2 shards, each with 1 replica
    replicasPerMaster: 1
    // ... (merged with above resource)
  }
}
```

- 2 shards × 6 GB = 12 GB total cache capacity (production).
- Cluster mode enables horizontal scaling and higher throughput.
- Dev/test: single node Basic tier (no clustering).

## 4. Private Endpoint

```bicep
resource privateEndpoint 'Microsoft.Network/privateEndpoints@2023-09-01' = {
  name: 'fxops-redis-pe-${environment}'
  location: location
  properties: {
    subnet: { id: dataSubnet.id }
    privateLinkServiceConnections: [{
      name: 'redis-connection'
      properties: {
        privateLinkServiceId: redisCache.id
        groupIds: ['redisCache']
      }
    }]
  }
}
```

Private DNS zone: `privatelink.redis.cache.windows.net` linked to VNet.

## 5. Spring Boot Lettuce Configuration

```yaml
# application-azure.yml
spring:
  data:
    redis:
      host: fxops-redis-${ENV}.redis.cache.windows.net
      port: 6380
      ssl:
        enabled: true
      password: ${AZURE_REDIS_KEY}   # or use Azure AD token
      lettuce:
        pool:
          min-idle: 5
          max-active: 20
          max-idle: 10
        cluster:
          refresh:
            adaptive: true
            period: 30s
```

For Managed Identity (Entra ID) access:
```java
// AzureRedisCredentialProvider using azure-identity
// Obtains AAD token, refreshes before expiry, passes to Lettuce
```

## 6. Azure AD (Entra ID) Access Control

- Redis supports Entra ID authentication (preview/GA on Premium tier).
- Pod managed identity gets `Redis Cache Contributor` or data-plane RBAC role.
- Access keys rotated every 90 days as fallback; stored in Key Vault.

## 7. Monitoring

- Azure Monitor metrics: `connectedclients`, `cachehits`, `cachemisses`, `usedmemory`, `serverLoad`.
- Alert rules:
  - Memory > 80% → P2 warning
  - Cache hit ratio < 90% → P3 warning
  - Server load > 70% → P2 warning
- Diagnostic settings → Log Analytics workspace.

## 8. Bicep Module Layout

```
DevOps/Azure/bicep/modules/redis/
├── main.bicep           ← instance + clustering + config
├── private-endpoint.bicep
├── rbac.bicep           ← Entra ID role assignments
└── alerts.bicep         ← Azure Monitor alert rules
```
