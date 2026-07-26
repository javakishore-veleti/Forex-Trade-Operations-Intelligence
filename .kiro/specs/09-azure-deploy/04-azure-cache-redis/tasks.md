# Tasks — Azure Cache for Redis (Cache on Azure)

> **Stage 3 of 3** (`requirements.md → design.md → tasks.md`). Execute top-to-bottom;
> each task is atomic and independently verifiable. Mark `[x]` as completed.

## 0. Scaffold

- [ ] 0.1 Create `DevOps/Azure/bicep/modules/redis/` with `main.bicep`, `private-endpoint.bicep`, `rbac.bicep`, `alerts.bicep`. (§8)

## 1. Redis Instance (Req 1)

- [ ] 1.1 Define Azure Cache for Redis resource: Premium P2 (prod) / Basic C0 (dev).
- [ ] 1.2 Set Redis version to 7.x.
- [ ] 1.3 Disable non-SSL port (`enableNonSslPort: false`).
- [ ] 1.4 Set minimum TLS version to 1.2.
- [ ] 1.5 Disable public network access.
- [ ] 1.6 Enable zone redundancy (zones [1,2,3]) for prod.
- [ ] 1.7 Configure `maxmemory-policy: allkeys-lru`. **Verify:** `az bicep build` succeeds.

## 2. Clustering (Req 1)

- [ ] 2.1 Enable clustering with 2 shards for Premium tier (prod only).
- [ ] 2.2 Set `replicasPerMaster: 1` for HA.
- [ ] 2.3 Document that dev/test uses single-node (no clustering). **Verify:** shard count in Bicep.

## 3. Private Endpoint (Req 1, 3)

- [ ] 3.1 Create Private Endpoint in data subnet targeting Redis cache.
- [ ] 3.2 Create Private DNS Zone `privatelink.redis.cache.windows.net` linked to VNet.
- [ ] 3.3 Create DNS A record for cache hostname. **Verify:** private endpoint in Bicep.

## 4. Spring Boot Lettuce Configuration (Req 2)

- [ ] 4.1 Create `application-azure.yml` snippet with `spring.data.redis.host`, port 6380, `ssl.enabled=true`.
- [ ] 4.2 Configure Lettuce pool: `min-idle=5`, `max-active=20`, `max-idle=10`.
- [ ] 4.3 Configure cluster refresh: `adaptive=true`, `period=30s` for cluster-aware mode.
- [ ] 4.4 Document Managed Identity token-based auth (Entra ID) as production preference.
- [ ] 4.5 Document access key fallback with Key Vault secret injection. **Verify:** valid Spring Boot YAML.

## 5. Security (Req 3)

- [ ] 5.1 Enable Entra ID authentication (Azure AD data-plane access).
- [ ] 5.2 Assign RBAC role to service managed identities.
- [ ] 5.3 Store access keys in Key Vault with 90-day rotation.
- [ ] 5.4 Document that non-TLS port is disabled — all traffic encrypted. **Verify:** RBAC assignments in Bicep.

## 6. Monitoring and Alerts (Req 5)

- [ ] 6.1 Enable diagnostic settings → Log Analytics workspace.
- [ ] 6.2 Create alert rule: memory usage > 80% → Sev2 warning.
- [ ] 6.3 Create alert rule: cache hit ratio < 90% → Sev3 warning.
- [ ] 6.4 Create alert rule: server load > 70% → Sev2 warning.
- [ ] 6.5 Document slow-log analysis via Azure Portal. **Verify:** alert rules in Bicep.

## 7. Cost Controls (Req 6)

- [ ] 7.1 Define dev parameters: Basic C0, no clustering, no zone redundancy.
- [ ] 7.2 Define prod parameters: Premium P2, 2 shards, zone-redundant.
- [ ] 7.3 Tag all resources with `project:fxops`, `environment:<env>`. **Verify:** tags in Bicep.
