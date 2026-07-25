# Tasks — MCP Server Setup (Agent Tool Layer)

> **Stage 3 of 3** (`requirements.md → design.md → tasks.md`). Derived from `design.md`. Execute
> top-to-bottom; each task is atomic, maps to specific files, and is independently verifiable.
> This is a **living checklist** — mark `[x]` as each task is completed and verified. Tags in
> parentheses trace to design sections (§) and requirements (Req / GP-Rq). All fixtures/examples
> use synthetic `FX-` identifiers only (GP-Rq-14).

## 0. Module scaffold — `shared-mcp-contracts`
- [ ] 0.1 Create Maven module `Middleware/shared-mcp-contracts` with `<parent>` → `Middleware/pom.xml`; add to parent `<modules>`. (§2)
- [ ] 0.2 Declare a compile-scope dependency on the `AGENT_TOOL_PROTOCOL` MCP SDK and add its server-starter to the `Parent_Build_Descriptor` dependency management so consuming services inherit it. (Req 1.2, §2)
- [ ] 0.3 Create package root `com.fxtradeops.mcp` with sub-packages `envelope/ action/ risk/ error/`. **Verify:** `mvn -pl Middleware/shared-mcp-contracts compile` green. (§2)

## 1. Shared contracts — agent envelope (Req 1)
- [ ] 1.1 `envelope/BusinessEntity` (`{type, id}`), `envelope/EnvelopeStatus` (`SUCCESS|PARTIAL|FAILURE`), `envelope/Fact` (`{key, value}`), `envelope/Evidence` (`{source, timestamp, reference}`) records. (§2.1)
- [ ] 1.2 `envelope/PermittedAction` fixed-catalogue enum (`RECALCULATE_RISK`, `START_RECONCILIATION`, `REPLAY_EVENT`, `QUARANTINE_TRADE`, `HOLD_EOD`, `NONE`, …); closed set, no free strings. (Req 1.1, 3.3)
- [ ] 1.3 `envelope/ToolEnvelope` record with fields `requestId, businessEntity, status, facts, violations, permittedActions, evidence, dataClassification, expiresAt`; field-level Javadoc on every field referencing its contract definition. (Req 1.1, 1.4, §2.1)
- [ ] 1.4 Static factories `ToolEnvelope.success(...)` / `.failure(entity, violations)` for uniform population. (§2.1)
- [ ] 1.5 Unit tests: envelope Jackson round-trip (ISO-8601 `expiresAt`, JSON numbers); `PermittedAction` catalogue closed. **Verify:** unit tests green. (§8, GP-Rq-12)

## 2. Shared contracts — structured action payload + risk (Req 1, 3)
- [ ] 2.1 `action/ActionRequest` record `{action, entityId, reasonCode, expectedVersion, idempotencyKey, approvalReference, dryRun}` with `BEAN_VALIDATION` constraints (`@NotNull action`, `@NotBlank entityId/reasonCode/idempotencyKey`). (§2.2, Req 3.2)
- [ ] 2.2 `risk/ToolRisk` enum `L|M|H` and `risk/GatedTool` annotation marking M/H tool methods for gateway enforcement. (Req 1.3, §2.3)
- [ ] 2.3 `error/ToolValidationError` structured body (never a raw exception); mapper from `BEAN_VALIDATION` failures → `FAILURE` `ToolEnvelope`. (Req 3.2, §5)
- [ ] 2.4 Unit tests: `ActionRequest` rejects blank `entityId`/`idempotencyKey`; validation failure surfaces as structured `ToolValidationError`, not an exception. All fixtures synthetic `FX-`. **Verify:** unit tests green. (§8, GP-Rq-12/14)

## 3. Per-service MCP server enablement (Req 2)
- [ ] 3.1 In each `Service_Module` (all `Middleware/` services except `shared-mcp-contracts` and `shared-domain-contracts`): add the MCP server starter (compile scope) + `shared-mcp-contracts` dependency. (Req 2.1)
- [ ] 3.2 Register a `MCPServer` bean with `server name = spring.application.name`; externalize a dedicated MCP port (distinct from REST port) via config. (Req 2.2/2.3, GP-Rq-11, §4)
- [ ] 3.3 Configure registration to expose only `@MCPTool`-annotated methods; fast-fail the service if the MCP server cannot bind. (Req 2.4/2.5)
- [ ] 3.4 Registration-layer test: annotated method is discoverable, a non-annotated helper is not; port-conflict → context fails to start. **Verify:** test green. (§8, Req 2.4/2.5)

## 4. Register read tools (Req 4 — Risk L)
- [ ] 4.1 `trade-lifecycle-service` `mcp/`: `getTrade`, `getTradeEvents`, `getTradeTimeline` — each returns `ToolEnvelope`, delegates to existing deterministic app layer, side-effect free. (Req 4.1, §3)
- [ ] 4.2 `risk-calculation-service` `mcp/`: `getRiskResult(tradeId)`, `getRuleTrace(calculationId)` → `ToolEnvelope`. (Req 4.3, §3)
- [ ] 4.3 `state-reconciliation-service` `mcp/`: `evaluateCanonicalState(tradeId)` → `ToolEnvelope` with `permittedActions` sourced from the deterministic reconciliation result (never LLM-authored). (Req 4.2, 3.3, GP-Rq-13)
- [ ] 4.4 `eod-processing-service` (`getRegionalCloseStatus`, `getReadinessStatusMap`) and `business-calendar-service` (`getBusinessCalendar`, `classifyBookingDate`) read tools → `ToolEnvelope`. (Req 4.4/4.5)

## 5. Register action tools (Req 4 — gated M/H) (§3, §6)
- [ ] 5.1 `state-reconciliation-service` `mcp/`: `startReconciliation(ActionRequest)` annotated `@GatedTool(risk=M)`; typed `ActionRequest` input, returns `ToolEnvelope`. (Req 4.2, §3)
- [ ] 5.2 Idempotency + optimistic-lock: honor `idempotencyKey` (GP-Rq-5) and `expectedVersion` (GP-Rq-6) before committing; commit exactly once. (§3)
- [ ] 5.3 Dry-run: when `dryRun=true`, run the deterministic simulation and return projected `facts`/`violations` with nothing committed. (§3, PRD action contract)
- [ ] 5.4 Approval backstop: when `approvalReference` is null, return `ToolEnvelope{status=FAILURE, violations=[APPROVAL_REFERENCE_REQUIRED]}` and commit no side effect. (Req 3.4, §6)

## 6. Local MCP gateway / transport config (Req 5; §4)
- [ ] 6.1 Configure SSE-over-HTTP transport for each service's `MCPServer`; document stdio as single-process fallback only. (§4)
- [ ] 6.2 Create `DevOps/Local/AGENT_PLATFORM/mcp-servers.json` listing every `MCPServer` endpoint `{server name, host, port}` using `CONTAINER_RUNTIME` service names (not `localhost`); no credentials, env-var placeholders only. (Req 5.1/5.3/5.5)
- [ ] 6.3 Mount `mcp-servers.json` into the `AGENT_PLATFORM` local compose service so it is read at startup and all tools become discoverable. (Req 5.2)
- [ ] 6.4 Document the "new `MCPServer` → update `mcp-servers.json` in the same PR" rule in the gateway config header. (Req 5.4)

## 7. Envelope ↔ MCP schema mapping (Req 1, 3; §5)
- [ ] 7.1 Verify Spring AI derives each tool `inputSchema` from `ActionRequest`/scalar keys and structured output from `ToolEnvelope`; add correlation `requestId`/MDC honoring inbound `X-Correlation-Id`. (§5, GP-Rq-2)
- [ ] 7.2 Confirm no untyped/string-blob tool entry point exists — action tools accept only `ActionRequest`. (§5, PRD safety principle)

## 8. Tests — local end-to-end (GP-Rq-12; §8)
- [ ] 8.1 Unit: `@GatedTool` with null `approvalReference` → `FAILURE` + `APPROVAL_REFERENCE_REQUIRED`; action `dryRun=true` commits nothing. (§8)
- [ ] 8.2 Integration (`INTEGRATION_TEST_HARNESS`): boot `trade-lifecycle-service` MCP server, call `getTrade("FX-000001")` **through the MCP transport as a client**, assert a valid `ToolEnvelope` (`requestId` present, `businessEntity.id="FX-000001"`, `status=SUCCESS`, populated `facts`, deterministic `permittedActions`). (§8, Req 4.1)
- [ ] 8.3 All fixtures use synthetic `FX-` ids and fictional names. (GP-Rq-14, Req 1.5/3.5)

## 9. Verification & tracking
- [ ] 9.1 `mvn -pl Middleware/shared-mcp-contracts verify` + `mvn -pl <each service> verify` — build + all tests green. (GP-Rq-12.5)
- [ ] 9.2 Update `.kiro/specs/MASTER-PLAN.md`: mark `06-local-deploy/01-mcp-server-setup` design+tasks complete.
- [ ] 9.3 Commit via `scripts/commit-specs.sh` / normal commit.

---
**Completion:** 0 / 32 tasks. Update this line as tasks are ticked.
