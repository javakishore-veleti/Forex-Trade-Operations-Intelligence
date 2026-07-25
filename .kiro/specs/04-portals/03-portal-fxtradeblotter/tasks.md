# Tasks — FX Trade Blotter Portal (Broker-Facing)

> **Stage 3 of 3** (`requirements.md → design.md → tasks.md`). Derived from `design.md`. Execute
> top-to-bottom; each task is atomic, maps to specific files, and is independently verifiable.
> This is a **living checklist** — mark `[x]` as each task is completed and verified. Tags in
> parentheses trace to design sections (§) and requirements (Req). This is an **Angular standalone
> frontend** (`Portals/FXTradeBlotter/`) — verification uses `ng build` / `ng test`, not Maven.

## 0. Workspace scaffold
- [ ] 0.1 Create standalone Angular workspace `Portals/FXTradeBlotter/` at the pinned `FRONTEND_FRAMEWORK` major, standalone-component mode (no `NgModule`), TypeScript strict. *(products resolved from technology-stack)* (§1, §2, Req 5.1)
- [ ] 0.2 `app.config.ts` bootstrap providers: `provideRouter`, `provideHttpClient(withInterceptors(...))`; `app.component.ts` shell with nav + router-outlet. (§2)
- [ ] 0.3 `src/environments/environment.ts` + `environment.prod.ts` with API base URLs, polling interval (default 30s), display thresholds — all externalized. (§2, Req 5.3)
- [ ] 0.4 `core/config/AppConfigService` reading environment config. **Verify:** `ng build` succeeds.

## 1. Routing (§3)
- [ ] 1.1 `app.routes.ts` with lazy `loadComponent` routes `/positions` (default), `/exposure`, `/settlement`, `/counterparty-exposure`, wildcard → `/positions`. (§3)
- [ ] 1.2 Placeholder standalone components for the four views so routing resolves. **Verify:** `ng build` green; each route loads.

## 2. Typed models (§2, §4)
- [ ] 2.1 `core/models`: `Position`, `ExposureGroup`, `SettlementRow`, `Page<T>`, `CounterpartyExposure`, `UiError`; enums `TradeStatus`, `RiskLevel`, `Direction` mirroring `shared-domain-contracts`. (§2, §4)

## 3. HTTP interceptors — cross-cutting (§4, §6)
- [ ] 3.1 `core/http/correlation-id.interceptor.ts`: attach `X-Correlation-Id` (UUID) to every outbound request, reused across a polling cycle. (§4, §6, Req analogue)
- [ ] 3.2 `core/http/error.interceptor.ts`: map non-2xx/network errors → typed `UiError{code,userMessage,retryable}`; never surface raw internals. (§4, §6, Req 5.5)
- [ ] 3.3 `core/auth/auth-placeholder.interceptor.ts`: no-op with `// TODO(phase-6)` bearer-token marker; no committed secrets. (§4, §6, Req 5.4)

## 4. API client services (§4)
- [ ] 4.1 `services/position-api.service.ts` → `GET /api/v1/blotter/positions` → `Position[]`. (§4, Req 1.1)
- [ ] 4.2 `services/exposure-api.service.ts` → `GET /api/v1/risk/exposure?groupBy=pair,region` → `ExposureGroup[]`. (§4, Req 2.1)
- [ ] 4.3 `services/settlement-api.service.ts` → `GET /api/v1/blotter/settlements` (valueDate/page/size) → `Page<SettlementRow>`. (§4, Req 3.1)
- [ ] 4.4 `services/counterparty-api.service.ts` → `GET /api/v1/risk/counterparty-exposure` → `CounterpartyExposure[]`. (§4, Req 4.2)

## 5. Shared UI + grid (§5, §6)
- [ ] 5.1 `shared/grid/VirtualBlotterGrid` over CDK `cdk-virtual-scroll-viewport` with declarative column defs. (§5)
- [ ] 5.2 `shared/ui`: `StatusBadge`, `BreachIndicator`, `FreshnessStamp`, `ErrorPanel`, `RetryButton` — all icon+text, WCAG 2.1 AA. (§6, Req 5.2)
- [ ] 5.3 `services/refresh.service.ts`: shared `timer(0,intervalMs)` clock from config; pause on `visibilitychange` hidden, resume on focus. (§5, Req 1.3)

## 6. Feature views (§3)
- [ ] 6.1 `LivePositionView`: virtual grid of net long/short/net notional + trade count per `currencyPair`; polls via `RefreshService`; threshold highlight (accessible); row → `/settlement?currencyPair=`. (Req 1)
- [ ] 6.2 `ExposureView`: groups by `currencyPair` + `regionCode` with riskAmount/riskCurrency/RiskLevel/limit/utilization; breach indicator; freshness stamp; read-only. (Req 2)
- [ ] 6.3 `SettlementStatusView`: rows for today + next business date grouped by `valueDate`; columns per Req 3.2; at-risk flag; filters (valueDate/status/pair); pagination (default 25); optional TraderDesk deep-link. (Req 3)
- [ ] 6.4 `CounterpartyExposureView`: per-counterparty aggregate exposure/limit/utilization/RiskLevel; 80% warning + 100% breach indicators (accessible); expandable contributing trades; freshness stamp. (Req 4)

## 7. Tests — component + e2e (§7; Req 5.6)
- [ ] 7.1 Service unit tests: each `*ApiService` attaches `X-Correlation-Id` and maps errors to `UiError`; `RefreshService` emits on interval + pauses when hidden. (§7)
- [ ] 7.2 Component unit tests: each view renders `FX-` rows, applies threshold/breach indicators, paginates/filters (settlement), expands rows (counterparty). (§7)
- [ ] 7.3 E2e: `/positions` grid + accessible threshold indicator; `/settlement` filter + pagination; `/counterparty-exposure` 80%/100% + expansion; simulated API failure → `ErrorPanel` + retry. (§7)
- [ ] 7.4 All fixtures/mocks use synthetic `FX-` ids and fictional counterparty/region names. (Req 5.6)

## 8. Verification & tracking
- [ ] 8.1 `ng build` — production build succeeds with no errors. (§1)
- [ ] 8.2 `ng test` — all component/service unit tests green. (§7)
- [ ] 8.3 Update `MASTER-PLAN.md`: mark `04-portals/03-portal-fxtradeblotter` design+tasks+code complete.
- [ ] 8.4 Commit via `scripts/commit-specs.sh` / normal commit.

---
**Completion:** 0 / 24 tasks. Update this line as tasks are ticked.
