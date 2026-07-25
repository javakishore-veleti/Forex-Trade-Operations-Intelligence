# Tasks — TraderDesk Portal (Customer-Facing Trader UI)

> **Stage 3 of 3** (`requirements.md → design.md → tasks.md`). Derived from `design.md`. Execute
> top-to-bottom; each task is atomic, maps to specific files, and is independently verifiable.
> This is a **living checklist** — mark `[x]` as each task is completed and verified. Tags in
> parentheses trace to design sections (§) and requirements (Req). This is an **Angular standalone
> frontend** under `Portals/TraderDesk/`; verification uses `ng build` / `ng test`, not `mvn`.

## 0. Application scaffold
- [ ] 0.1 Scaffold standalone Angular 19.x app at `Portals/TraderDesk/` (`bootstrapApplication`, no NgModules); pin to the same `FRONTEND_FRAMEWORK` major as other portals. (§1, §2, Req 5.1)
- [ ] 0.2 `app.config.ts` providers: router, `provideHttpClient(withInterceptors(...))`; `app.component.ts` shell (nav + `<router-outlet>`). (§2)
- [ ] 0.3 `core/config/app-config.token.ts` + `app-config.ts` + `src/environments/*` — externalize API base URLs and poll interval (no hard-coded URLs). (§2, §5, Req 5.3)
- [ ] 0.4 `core/auth/auth.placeholder.ts` — TODO marker for token auth; assert no secrets/credentials committed. (§2, §7, Req 5.4)
- [ ] 0.5 Strict-mode `tsconfig`; `shared/models/` typed DTOs (`TradeState`, `TimelineEntry`, `RiskExplanation`, `ContributingFactor`, `PositionGroup`, `BookTrade`, `TradeStatus`, `RiskLevel`). **Verify:** `ng build` succeeds. (§1, §2)

## 1. Routing (§3)
- [ ] 1.1 `app.routes.ts` lazy standalone routes: `''→search`, `my-trades/:tradeId`, `my-trades/:tradeId/risk`, `positions`, `books/:tradingBookId`, `**→NotFound`. (§3)
- [ ] 1.2 Route-param wiring so param changes re-trigger loads; deep-linkable views. **Verify:** `ng build` green; routes resolve. (§3, §5)

## 2. Cross-cutting HTTP concerns (§4, §7)
- [ ] 2.1 `core/http/correlation-id.interceptor.ts` — set `X-Correlation-Id` on every outbound request. (§4, §7)
- [ ] 2.2 `core/http/error.interceptor.ts` — map non-2xx/network errors to `UiError{title,message,retryable}`, strip all backend internals/stack traces. (§4, §7, §9, Req 5.5)

## 3. Typed API clients (§4)
- [ ] 3.1 `api/trade-lifecycle.client.ts` — `getState`, `getTimeline`, `getExpectedLifecycle` (base URL from `AppConfig`). (§4, Req 1)
- [ ] 3.2 `api/risk-explainability.client.ts` — `getExplanation`, `getPriorExplanation`. (§4, Req 2)
- [ ] 3.3 `api/exposure.client.ts` — `getPositionSummary`, `getBookTrades`. (§4, Req 3, 4)

## 4. Shared accessible components (§2)
- [ ] 4.1 `shared/components/lifecycle-stepper/` — accessible complete/current/pending step indicator (WCAG 2.1 AA). (Req 1.3, 5.2)
- [ ] 4.2 `shared/components/risk-level-badge/` — `RiskLevel` conveyed by text+shape, not colour alone. (Req 3.5, 5.2)
- [ ] 4.3 `shared/components/factor-bar/` — contributing-factor share breakdown. (Req 2.3)
- [ ] 4.4 `shared/components/error-panel/`, `empty-state/`, `loading-spinner/` + `shared/a11y/` helpers. (§5, §9, Req 5.5)

## 5. Feature view — Trade lifecycle status (Req 1)
- [ ] 5.1 `features/trade-search/` — accept `tradeId`, navigate to `my-trades/:tradeId`. (Req 1.1)
- [ ] 5.2 `features/my-trades/` TradeStatusView — display all Req 1.2 fields + ordered timeline via `lifecycle-stepper`; read-only, no action controls. (Req 1.2, 1.3, 1.5)
- [ ] 5.3 `TRADE_FAILED`/anomaly → clearly labelled indicator, no internal/error details; link to risk drill-down when a risk result exists. (Req 1.4, 2.1)

## 6. Feature view — Risk explanation (Req 2)
- [ ] 6.1 `features/risk-explanation/` RiskExplanationView — display `riskAmount`, `riskCurrency`, `riskLevel`, `ruleVersion`, `contributingFactors` (via `factor-bar`), `rulesFired` (synthetic ids). (Req 2.2, 2.3, 2.5)
- [ ] 6.2 Current-vs-prior comparison highlighting changed factors + deltas; read-only. (Req 2.4, 2.6)

## 7. Feature view — Position summary (Req 3)
- [ ] 7.1 `features/position-summary/` — aggregate notional+risk grouped by `currencyPair` and `regionCode`; per-group totals, `RiskLevel`, trade count, freshness timestamp. (Req 3.1, 3.2, 3.3)
- [ ] 7.2 Configurable polling refresh (default 60s from `AppConfig`); non-colour-only risk indicators. (Req 3.4, 3.5, §5)

## 8. Feature view — Trading book (Req 4)
- [ ] 8.1 `features/trading-book/` TradingBookView — list book trades with Req 4.1 columns; rows link to `my-trades/:tradeId`. (Req 4.1, 4.4)
- [ ] 8.2 Sort (`tradeDate`/`notionalAmount`/`RiskLevel`), filter (`TradeStatus`/`RiskLevel`), paginate (configurable default 25). (Req 4.2, 4.3, 4.5)

## 9. Tests (§10; Req 5.6)
- [ ] 9.1 Component specs (`ng test`): each view renders `loading/loaded/empty/error`; stepper/factor-bar/risk-badge behavior. (§10, Req 1.3, 2.3, 3.5)
- [ ] 9.2 Interceptor/client unit tests: correlation-id header set; error interceptor strips internals → `UiError`; clients use configured base URL. (§4, §7, Req 5.5)
- [ ] 9.3 E2E (`ng e2e` vs mocked API): search→status→risk drill-down; position poll refresh; book filter+paginate+row-link. (§10)
- [ ] 9.4 All fixtures/mocks use synthetic `FX-` ids and fictional names. **Verify:** `ng test` green. (Req 5.6)

## 10. Verification & tracking
- [ ] 10.1 `ng build` (prod) + `ng test` (and `ng e2e`) all green. (§10)
- [ ] 10.2 Update `MASTER-PLAN.md`: mark `02-portal-traderdesk` design+tasks+code complete.
- [ ] 10.3 Commit via `scripts/commit-specs.sh` / normal commit.

---
**Completion:** 0 / 26 tasks. Update this line as tasks are ticked.
