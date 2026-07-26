# Tasks — Azure Database for PostgreSQL (Relational Store on Azure)

> **Stage 3 of 3** (`requirements.md → design.md → tasks.md`). Execute top-to-bottom;
> each task is atomic and independently verifiable. Mark `[x]` as completed.

## 0. Scaffold

- [ ] 0.1 Create `DevOps/Azure/bicep/modules/postgres/` with `main.bicep`, `parameters.json`. (§2)
- [ ] 0.2 Create `DevOps/Azure/bicep/modules/postgres/pgbouncer.bicep`. (§3)
- [ ] 0.3 Create `DevOps/Azure/bicep/modules/postgres/firewall.bicep`. (§5)

## 1. Flexible Server Instance (Req 1)

- [ ] 1.1 Define Flexible Server resource with PostgreSQL 16.x, zone-redundant HA for prod, single-zone for dev.
- [ ] 1.2 Configure SKU: General Purpose `Standard_D4ds_v5` (prod) / Burstable `Standard_B2ms` (dev).
- [ ] 1.3 Configure storage: Premium SSD, auto-grow enabled, max 500 GB.
- [ ] 1.4 Configure network: delegated subnet (data subnet from VNet module), private DNS zone.
- [ ] 1.5 Enable Entra ID + password authentication (`authConfig`).
- [ ] 1.6 Configure data encryption with customer-managed Key Vault key.
- [ ] 1.7 Output server FQDN, server ID. **Verify:** `az bicep build` succeeds.

## 2. PgBouncer Configuration (Req 2)

- [ ] 2.1 Enable built-in PgBouncer (`pgbouncer.enabled = true`).
- [ ] 2.2 Set pool mode to `transaction`.
- [ ] 2.3 Set `default_pool_size = 50`, `max_client_conn = 500`, `min_pool_size = 10`.
- [ ] 2.4 Document connection endpoint: `<server>.postgres.database.azure.com:6432`. **Verify:** PgBouncer params in Bicep output.

## 3. Server Parameters (Req 5)

- [ ] 3.1 Define custom parameter configurations: `shared_buffers`, `effective_cache_size`, `work_mem`, `max_connections=200`, `checkpoint_completion_target=0.9`.
- [ ] 3.2 Enable slow query logging: `log_min_duration_statement = 500`.
- [ ] 3.3 Enforce TLS: `require_secure_transport = ON`.
- [ ] 3.4 Set `log_statement = ddl` for schema change auditing. **Verify:** parameters defined in Bicep.

## 4. Security and Network (Req 3)

- [ ] 4.1 Confirm no public endpoint (`publicNetworkAccess: Disabled` implicit with VNet integration).
- [ ] 4.2 Create private DNS zone `privatelink.postgres.database.azure.com` linked to VNet.
- [ ] 4.3 Store admin password in Key Vault with 90-day rotation policy.
- [ ] 4.4 Create Managed Identity for the server (CMK access to Key Vault). **Verify:** no public IP in Bicep output.

## 5. Backup and DR (Req 6)

- [ ] 5.1 Set backup retention to 30 days.
- [ ] 5.2 Enable geo-redundant backup for prod, disable for dev.
- [ ] 5.3 Document PITR procedure in `docs/runbooks/pg-azure-restore.md`.
- [ ] 5.4 Add pre-migration manual snapshot step to Flyway migration docs. **Verify:** backup config in Bicep.

## 6. Flyway Migration Integration (Req 4)

- [ ] 6.1 Create Helm init container template for Flyway migration (Flyway 10 image).
- [ ] 6.2 Configure JDBC URL pointing to PgBouncer port (6432) with `sslmode=require`.
- [ ] 6.3 Map credentials from Key Vault (via CSI driver) to init container env vars.
- [ ] 6.4 Configure `FLYWAY_SCHEMAS` per service for schema-per-service isolation. **Verify:** `helm template` shows init container.

## 7. Spring Boot Connection Config (Req 2, 5)

- [ ] 7.1 Create `application-azure.yml` snippet with JDBC URL, HikariCP pool settings.
- [ ] 7.2 Set connection timeout 5s, statement timeout 30s.
- [ ] 7.3 Document property overrides for dev vs prod in values files. **Verify:** YAML is valid Spring Boot config.

## 8. Cost Controls (Req 7)

- [ ] 8.1 Define dev parameters: Burstable tier, single-zone, smaller storage.
- [ ] 8.2 Define prod parameters: General Purpose, zone-redundant, Premium SSD.
- [ ] 8.3 Tag all resources with `project:fxops`, `environment:<env>`. **Verify:** tags in Bicep.
