# Tasks — n8n Local Setup (Agent Host / MCP Client)

> **Stage 3 of 3** (`requirements.md → design.md → tasks.md`). Derived from `design.md`. Execute
> top-to-bottom; each task is atomic, maps to specific files, and is independently verifiable.
> This is a **living checklist** — mark `[x]` as each task is completed and verified. Tags in
> parentheses trace to design sections (§) and requirements (Req / GP-Rq). All ids are synthetic `FX-`.

## 0. Compose scaffold — n8n instance (Req 1)
- [ ] 0.1 Create `DevOps/Local/n8n/docker-compose.yaml` with the `n8n` service: pinned `n8nio/n8n:<tag>` image (never `latest`), volume `n8n_data:/home/node/.n8n`, port `5678:5678`, joined to the external `fxops-local` network. (§2, Req 1.1/1.2)
- [ ] 0.2 Configure env for `DB_TYPE=postgresdb` → the `RELATIONAL_STORE` service, `EXECUTIONS_MODE=queue` + a `n8n-worker` service, and `N8N_ENCRYPTION_KEY`; all sensitive values reference `.env`. (§2, Req 1.3)
- [ ] 0.3 Add `DevOps/Local/n8n/.env.example` (placeholders, committed) and `.env` (git-ignored); add `.env` to `.gitignore`. No real secrets committed. (§2, Req 1.4; GP-Rq-14)
- [ ] 0.4 Add `depends_on` + healthchecks so n8n starts after `RELATIONAL_STORE` and `CACHE`. **Verify:** `docker compose -f DevOps/Local/n8n/docker-compose.yaml up -d` brings n8n healthy; UI answers on `http://localhost:5678`; workflows/credentials survive a container restart. (§2, Req 1.1)

## 1. MCP client wiring (Req 3)
- [ ] 1.1 Create `DevOps/Local/n8n/mcp-servers.json` listing each `Service_Module` MCP server with its `CONTAINER_RUNTIME` base URL + MCP port (service name, not `localhost`), mirroring the `06-local-deploy/01` gateway registry. (§3, Req 3.1/3.2)
- [ ] 1.2 Write `DevOps/Local/n8n/provision-mcp-credentials.sh` that creates one `MCPClientCredential` per server via the n8n public REST API (`POST /api/v1/credentials`), never direct DB writes; placeholder `authToken` with a production-injection TODO marker. (§3, Req 3.1/3.4)
- [ ] 1.3 Make the script idempotent (query-by-name, update-not-duplicate) and add a post-provision connectivity probe that logs `WARN` for any unreachable `MCPServer`. **Verify:** run twice → exactly one credential per server, no duplicates, no committed secret; an intentionally down server logs `WARN` without failing. (§3, Req 3.3/3.5)

## 2. Webhook endpoints — sidecar triggers (Req 4)
- [ ] 2.1 Ensure each sidecar-triggered workflow exposes a webhook trigger node; document `DevOps/Local/n8n/sidecar-webhooks.md` mapping each `Sidecar` (`kpi-anomaly-detector`, `dlq-cluster-analyzer`, `capacity-forecast-model`, `log-normalizer`) → `WebhookEndpoint` → `WorkflowJSON`. (§4, Req 4.1/4.2)
- [ ] 2.2 Document hostname resolution in `sidecar-webhooks.md`: service name `n8n` for in-network sidecars, `localhost` for the developer terminal; all example `AnomalyEnvelope` payloads use synthetic `FX-` ids + fictional detector output. (§4, Req 4.4/4.5)
- [ ] 2.3 Add a webhook verification step (POST empty body → assert `200`/`201`) invoked by the smoke test. **Verify:** every registered `WebhookEndpoint` returns `200`/`201`. (§4, Req 4.3)

## 3. Workflow import + supervisor skeleton (Req 2)
- [ ] 3.1 Write `DevOps/Local/n8n/import-workflows.sh` importing every `WorkflowJSON` from `Agents/workflows/` via the n8n public API/CLI only; per-file name+status logging. (§5, Req 2.1/2.3/2.5)
- [ ] 3.2 Make import idempotent (key by workflow `name`, update-not-duplicate) and continue-on-error (log filename+error, keep going). (§5, Req 2.2/2.4)
- [ ] 3.3 Create `Agents/workflows/fx-supervisor-skeleton.json`: Webhook trigger → `MODEL_TIER_FAST` extract → `MODEL_TIER_MID` router → one read-only (`ToolRisk=L`) MCP tool-call node → Respond returning the `ToolEnvelope`. (§5)
- [ ] 3.4 **Verify:** run `import-workflows.sh` twice → no duplicate workflows; a deliberately malformed JSON is logged-and-skipped, not fatal; the supervisor skeleton appears in the n8n UI. (§5, Req 2.2/2.4)

## 4. End-to-end tool-call validation (Req 5)
- [ ] 4.1 Author `DevOps/Local/SMOKE-TEST.md` describing the step-by-step chain: POST synthetic `AnomalyEnvelope` → workflow run in execution log → ≥1 MCP tool called → `ToolEnvelope` with `status = SUCCESS`. (§6, Req 5.1/5.2)
- [ ] 4.2 Implement `DevOps/Local/smoke-test.sh`: single script running all steps, synthetic `FX-000001` payload, exit `0` on success / non-zero on failure, per-step pass/fail summary. (§6, Req 5.3/5.4/5.5)
- [ ] 4.3 **Verify:** with the full stack up, `smoke-test.sh` exits `0` and asserts a live MCP tool call returned a valid `ToolEnvelope` (`status = SUCCESS`) for `FX-000001`. (§6, §8, Req 5.2)

## 5. Approval-gate validation (HITL before sensitive tools)
- [ ] 5.1 Add a sensitive-tool branch to the supervisor skeleton: `MODEL_TIER_DEEP` drafts an approval summary → n8n `Wait` node (resume on approval webhook/UI action) → sensitive (`ToolRisk` M/H) MCP tool-call node placed strictly *after* the Wait node. (§6, technology-stack Req 7.3)
- [ ] 5.2 **Verify:** trigger the sensitive path → execution pauses at the `Wait` node and **no tool call is emitted** until approval is posted; after approval it resumes and completes. (§6, §8)

## 6. Composition & verification
- [ ] 6.1 Document the full local bring-up order in `DevOps/Local/SMOKE-TEST.md`: infra → services + MCP servers → gateway → sidecars → n8n → import + provision scripts → smoke test. (§7)
- [ ] 6.2 **Verify:** a clean end-to-end run (compose up all phases + scripts + `smoke-test.sh` green) proves the stack is runnable and an agent executes end-to-end. (§7, §8)
- [ ] 6.3 Update `MASTER-PLAN.md`: mark `06-local-deploy/03-n8n-local-setup` design+tasks complete.
- [ ] 6.4 Commit via `scripts/commit-specs.sh` / normal commit.

---
**Completion:** 0 / 20 tasks. Update this line as tasks are ticked.
