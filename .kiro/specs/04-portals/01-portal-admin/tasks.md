# Tasks — Admin Portal (Operations & Risk)

> **Stage 3 of 3** (`requirements.md → design.md → tasks.md`). Derived from `design.md`. Execute
> top-to-bottom; each task is atomic, maps to specific files, and is independently verifiable.
> This is a **living checklist** — mark `[x]` as each task is completed and verified. Tags in
> parentheses trace to design sections (§) and requirements (Req). This is an **Angular standalone
> frontend** — verification uses `ng build` / `ng test` / `ng e2e`, never `mvn`.

## 0. Workspace scaffold
- [ ] 0.1 Create Angular standalone workspace `Portals/Admin/` (project `admin-portal`), strict TypeScript, routing enabled, `NgModule`-free bootstrap. (§2, Req 6.1)
- [ ] 0.2 `src/environments/environment.ts` + `environment.prod.ts` with API base URLs + poll intervals as config placeholders (no secrets). (§7, Req 6.3, 6.4)
- [ ] 0.3 `core/config/app-config.service.ts` reading environment; `core/auth/auth.placeholder.ts` marking Phase-N token-auth wiring, no credentials committed. (§2, Req 6.3, 6.4)
- [ ] 0.4 `app.config.ts` (providers: router, `HttpClient` with interceptors) + `app.component.ts` shell (nav, role banner, approval-inbox badge). **Verify:** `ng build` succeeds. (§2)

## 1. Routing (Req 1–5)
- [ ] 1.1 `app.routes.ts` top-level lazy standalone routes: `/eod` (default), `/trades`, `/trades/:tradeId`, `/risk`, `/exceptions`, `/approvals`, `**` NotFound. (§3)
- [ ] 1.2 Route-level smoke: each route resolves to its standalone component; default redirect to `/eod`. **Verify:** `ng test` route specs green. (§3)

## 2. HTTP cross-cutting interceptors (§4, §7)
- [ ] 2.1 `core/http/correlation-id.interceptor.ts` — attach `X-Correlation-Id` to every outbound request, one id per user flow. (§4, §7)
- [ ] 2.2 `core/http/error.interceptor.ts` — map non-2xx + network errors to typed `AppError {code, userMessage, correlationId}`; strip stack traces/internal detail. (§7, Req 6.5)
- [ ] 2.3 Specs: correlation header present on outbound; error envelope mapped, no raw detail leaked. **Verify:** `ng test` interceptor specs green. (Req 6.5)

## 3. API client services (§4)
- [ ] 3.1 `core/models/` TypeScript interfaces mirroring backend DTOs (`TradeStatus`, `RiskResult`, `RegionalCloseStatus`, `ReconciliationResult`, `ExceptionEntry`, `ApprovalRequest`). (§2, §4)
- [ ] 3.2 `core/api/trade-lifecycle.client.ts` (`getState`/`getTimeline`/`getExpectedLifecycle`) + `risk.client.ts` (`getRiskResult`/`getAggregation`). (§4)
- [ ] 3.3 `core/api/eod.client.ts` (`getRegionalStatuses`/`getGlobalStatus`) + `state-reconciliation.client.ts` (`getReconciliation` → `{violatedInvariants, permittedActions}`). (§4)
- [ ] 3.4 `core/api/exception-materiality.client.ts` (`listExceptions`/`submitResolution`) + `approval.client.ts` (`listPending`/`postDecision`, AGENT_PLATFORM webhook). (§4, §6)
- [ ] 3.5 All clients read base URLs from `AppConfigService` (no hard-coded URLs). **Verify:** `ng test` client specs green with mocked `HttpClient`. (Req 6.3)

## 4. Shared presentational + a11y layer (§2, §7)
- [ ] 4.1 `shared/components/`: `status-badge`, `breach-indicator` (colour **plus** icon/label), `confirm-dialog`, `error-panel`, `retry-button`, `loading-spinner`, `empty-state`. (§2, Req 3.5)
- [ ] 4.2 `shared/a11y/` focus-trap + live-region announcer; `ViewState<T>` helper for loading/empty/error/ready. (§5, §7, Req 6.2)

## 5. Trade Investigation view (Req 1)
- [ ] 5.1 `features/trade-investigation/` search (`FX-######`) → route to `/trades/:tradeId`. (§3.1, Req 1.1)
- [ ] 5.2 Investigation view fan-out (`forkJoin`): current `TradeStatus`, ordered timeline, `RiskResult`, `SequenceViolation` anomalies, `DLQTopic` presence + `dlq.failure.reason`, reconciliation `violatedInvariants`. (§3.1, Req 1.2–1.4)
- [ ] 5.3 Action-gate: render replay/reconcile actions **only** when the matching `permittedAction` is present; confirm before submit. (§3.1, §6, Req 1.5)
- [ ] 5.4 Specs: unauthorized action hidden; partial fan-out degrades gracefully. **Verify:** `ng test` green. (Req 1.5, §5)

## 6. EOD Readiness Dashboard (Req 2)
- [ ] 6.1 `features/eod-dashboard/` render per-`RegionCode` `RegionalCloseStatus` + `GlobalConsolidationStatus`, `GlobalBusinessDate`, per-region elapsed. (§3.2, Req 2.1, 2.5)
- [ ] 6.2 Configurable poll (default 30s via config, RxJS `timer`+`switchMap`, pause on hidden tab); state colour **plus** label/icon for `IN_PROGRESS`/`READY`/`BLOCKED`/`CLOSED`. (§3.2, §5, Req 2.2, 2.3)
- [ ] 6.3 `BLOCKED` region shows `blockerCode`+`blockerDescription`, deep-link to `/exceptions?region=<code>`. **Verify:** `ng test` green. (Req 2.4)

## 7. Risk Aggregation view (Req 3)
- [ ] 7.1 `features/risk-aggregation/` group totals by `regionCode` + `tradingBookId`: `riskAmount`, `Limit`, utilization %, `RiskLevel`. (§3.3, Req 3.1, 3.2)
- [ ] 7.2 Breach highlight with icon/text label (not colour alone) + link to affected trades; show most-recent-calculation timestamp (staleness). **Verify:** `ng test` green. (Req 3.3, 3.4, 3.5)

## 8. Exception Management Queue (Req 4)
- [ ] 8.1 `features/exception-queue/` list EOD + DLQ exceptions; columns type/`tradeId`/`regionCode`/created-at/status; filters type/region/tradeId/date-range. (§3.4, Req 4.1–4.3)
- [ ] 8.2 Resolution action button only when a permitted action exists, routed via service API **with confirm**; `PoisonMessage` flagged with "requires manual review", no auto-replay. **Verify:** `ng test` green. (Req 4.4, 4.5)

## 9. Approval Inbox — HITL (Req 5)
- [ ] 9.1 `features/approval-inbox/` list pending `AGENT_PLATFORM` requests for the user's role; configurable poll (default 15s); shell pending-count badge. (§6, Req 5.1, 5.6)
- [ ] 9.2 Request card: agent name, proposed-action description, deterministic impact/dryRun preview, risk class `M`/`H`, `approvalReference`; permitted controls derived from payload. (§6, Req 5.2)
- [ ] 9.3 Approve/Reject via `confirm-dialog`; POST decision (incl. `approvalReference`, reject note) to AGENT_PLATFORM webhook; guard blocks submit when `approvalReference` empty. **Verify:** `ng test` green incl. empty-reference guard. (§6, Req 5.3–5.5)

## 10. Cross-cutting NFRs (§7)
- [ ] 10.1 i18n-ready: user-facing strings via Angular i18n (`$localize`/message ids), no concatenated literals. (§7)
- [ ] 10.2 Confirm no secrets committed; all base URLs + intervals externalized in `environment*.ts`. (§7, Req 6.3, 6.4)
- [ ] 10.3 WCAG 2.1 AA pass across all views (semantic landmarks, keyboard nav, focus-trap dialogs, live regions, AA contrast, colour-plus-icon). (§7, Req 6.2)

## 11. Tests — component + e2e (§7 testing)
- [ ] 11.1 Component/service specs cover each view's loading/empty/error/ready + action-gate + approval guard. **Verify:** `ng test` all green. (§7)
- [ ] 11.2 e2e (Playwright vs mock API, synthetic `FX-` payloads): trade search→investigation, EOD poll + BLOCKED deep-link, risk breach indicator, exception filter + poison no-replay, approval approve/reject with `approvalReference`. (§7)
- [ ] 11.3 axe a11y scan integrated into e2e asserting WCAG 2.1 AA per view. (§7, Req 6.2)
- [ ] 11.4 All fixtures/e2e data use synthetic `FX-` ids + fictional names only. (Req 1.6, 6.6)

## 12. Verification & tracking
- [ ] 12.1 **Verify:** `ng build` (production) + `ng test` + `ng e2e` all green.
- [ ] 12.2 Update `MASTER-PLAN.md`: mark `04-portals/01-portal-admin` design+tasks+code complete.
- [ ] 12.3 Commit via `scripts/commit-specs.sh` / normal commit.

---
**Completion:** 0 / 34 tasks. Update this line as tasks are ticked.
