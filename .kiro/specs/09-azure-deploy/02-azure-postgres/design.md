# Design Document — Azure Database for PostgreSQL (Relational Store on Azure)

> **Stage 2 of 3** (`requirements.md → design.md → tasks.md`). Resolves `CloudTargetBinding`
> for `RELATIONAL_STORE` → Azure Database for PostgreSQL Flexible Server. Concrete Bicep configuration.

## 1. Overview

The platform `RELATIONAL_STORE` maps to **Azure Database for PostgreSQL Flexible Server** with
built-in PgBouncer for connection pooling. All services connect via PgBouncer (port 6432);
direct database access is restricted by VNet rules.

**Technology Bindings:**

| Technology Role | Azure Service | Configuration |
|---|---|---|
| `RELATIONAL_STORE` | Azure Database for PostgreSQL Flexible Server | 16.x, Zone-redundant HA |
| Connection Pool | Built-in PgBouncer | Transaction mode |
| Encryption at rest | Azure Key Vault CMK | Customer-managed |
| Credential management | Azure Key Vault | Rotated ≤ 90 days |
| Schema migration | Flyway (in-service) | Runs in init container |
| Identity access | Entra ID (Azure AD) | Managed Identity auth |

## 2. Instance Configuration

```bicep
// DevOps/Azure/bicep/modules/postgres/main.bicep (conceptual)
resource pgServer 'Microsoft.DBforPostgreSQL/flexibleServers@2023-12-01-preview' = {
  name: 'fxops-pg-${environment}'
  location: location
  sku: {
    name: environment == 'prod' ? 'Standard_D4ds_v5' : 'Standard_B2ms'
    tier: environment == 'prod' ? 'GeneralPurpose' : 'Burstable'
  }
  properties: {
    version: '16'
    administratorLogin: 'fxops_admin'
    administratorLoginPassword: adminPasswordSecret
    storage: {
      storageSizeGB: 128
      autoGrow: 'Enabled'
      tier: environment == 'prod' ? 'P30' : 'P10'
    }
    highAvailability: {
      mode: environment == 'prod' ? 'ZoneRedundant' : 'Disabled'
      standbyAvailabilityZone: '2'
    }
    backup: {
      backupRetentionDays: 30
      geoRedundantBackup: environment == 'prod' ? 'Enabled' : 'Disabled'
    }
    network: {
      delegatedSubnetResourceId: dataSubnet.id
      privateDnsZoneResourceId: pgPrivateDnsZone.id
    }
    authConfig: {
      activeDirectoryAuth: 'Enabled'
      passwordAuth: 'Enabled'
    }
    dataEncryption: {
      type: 'AzureKeyVault'
      primaryKeyURI: keyVaultKeyUri
      primaryUserAssignedIdentityId: pgIdentity.id
    }
  }
}
```

## 3. PgBouncer Configuration

```bicep
resource pgBouncerConfig 'Microsoft.DBforPostgreSQL/flexibleServers/configurations@2023-12-01-preview' = [for param in [
  { name: 'pgbouncer.enabled', value: 'true' }
  { name: 'pgbouncer.default_pool_size', value: '50' }
  { name: 'pgbouncer.max_client_conn', value: '500' }
  { name: 'pgbouncer.pool_mode', value: 'transaction' }
  { name: 'pgbouncer.min_pool_size', value: '10' }
]: {
  name: param.name
  parent: pgServer
  properties: { value: param.value, source: 'user-override' }
}]
```

Services connect to `fxops-pg-<env>.postgres.database.azure.com:6432` (PgBouncer port).

## 4. Server Parameters

```bicep
resource serverParams 'Microsoft.DBforPostgreSQL/flexibleServers/configurations@2023-12-01-preview' = [for param in [
  { name: 'shared_buffers', value: '262144' }           // 1GB at 8KB pages
  { name: 'effective_cache_size', value: '786432' }     // 6GB
  { name: 'work_mem', value: '65536' }                  // 64MB
  { name: 'max_connections', value: '200' }
  { name: 'checkpoint_completion_target', value: '0.9' }
  { name: 'log_min_duration_statement', value: '500' }
  { name: 'require_secure_transport', value: 'ON' }
]: {
  name: param.name
  parent: pgServer
  properties: { value: param.value, source: 'user-override' }
}]
```

## 5. Security Configuration

- **VNet Integration**: Delegated subnet (`data` subnet), no public access.
- **Private DNS Zone**: `privatelink.postgres.database.azure.com` linked to VNet.
- **TLS**: `require_secure_transport = ON`, minimum TLS 1.2.
- **Entra ID Auth**: Enabled — pods authenticate using Workload Identity → access token.
- **Key Vault**: Admin password + CMK stored in Key Vault; access via Managed Identity.

## 6. Flyway Migration Strategy

```yaml
# Helm init container in each service deployment
initContainers:
  - name: flyway-migrate
    image: flyway/flyway:10
    args: ["migrate"]
    env:
      - name: FLYWAY_URL
        value: "jdbc:postgresql://fxops-pg-${ENV}.postgres.database.azure.com:6432/fxops?sslmode=require"
      - name: FLYWAY_USER
        valueFrom: { secretKeyRef: { name: pg-credentials, key: username } }
      - name: FLYWAY_PASSWORD
        valueFrom: { secretKeyRef: { name: pg-credentials, key: password } }
      - name: FLYWAY_SCHEMAS
        value: "<service-specific-schema>"
    volumeMounts:
      - name: migrations
        mountPath: /flyway/sql
```

Each service owns its schema within the shared database (schema-per-service isolation).

## 7. Backup and DR

- Automated backups: 30-day retention, geo-redundant for prod.
- PITR: 5-minute granularity (continuous WAL backup).
- Pre-migration snapshot: manual via Azure CLI before schema changes.
- Restore runbook: documented in `docs/runbooks/pg-restore.md`.

## 8. Spring Boot Connection Configuration

```yaml
# values-prod.yaml (injected via ConfigMap/Secrets)
spring:
  datasource:
    url: jdbc:postgresql://fxops-pg-prod.postgres.database.azure.com:6432/fxops?sslmode=require
    username: ${AZURE_PG_USER}
    password: ${AZURE_PG_PASSWORD}
    hikari:
      maximum-pool-size: 20
      minimum-idle: 5
      connection-timeout: 5000
```

## 9. Bicep Module Layout

```
DevOps/Azure/bicep/modules/postgres/
├── main.bicep          ← server + HA + network + encryption
├── parameters.json     ← per-environment overrides
├── pgbouncer.bicep     ← PgBouncer configuration
└── firewall.bicep      ← VNet rules (delegated subnet only)
```
