# ADR-0019: Three Separate Angular Apps vs Micro-Frontend vs Monolithic SPA

## Status
Accepted

## Context
The platform serves three distinct user personas with different workflows:
- **Admin Portal** — operations team: trade investigation, EOD monitoring, HITL approvals, exception management
- **TraderDesk Portal** — front-office traders: position status, risk explanations, book views
- **FXTradeBlotter Portal** — middle-office: live positions, exposure, settlement tracking, counterparty views

Each persona has different release cadence, security requirements (Admin has elevated privileges),
and uptime expectations (TraderDesk is latency-sensitive during market hours).

## Decision
Build **three independent Angular 19 applications**, each with its own:
- `package.json`, build pipeline, and deployment artifact
- Route tree and lazy-loaded feature modules
- Authentication scope (Admin requires MFA + elevated role)

All three share a common `@fxops/ui-kit` library (published to a private npm registry) for
design system components (tables, charts, status badges, trade cards).

```
Portals/
├── admin-portal/          # Independent Angular app
├── trader-desk-portal/    # Independent Angular app
├── fx-trade-blotter/      # Independent Angular app
└── libs/ui-kit/           # Shared component library
```

## Alternatives Considered

| Alternative | Why rejected |
|-------------|-------------|
| **Monolithic SPA (single app, role-based routing)** | Couples release cycles; Admin changes risk breaking TraderDesk; bundle size grows for all users regardless of persona; security boundary harder to enforce |
| **Module Federation micro-frontend** | Significant runtime complexity (shared dependency negotiation, version skew); overkill for 3 apps maintained by one team; debugging cross-app issues is painful |
| **Web Components micro-frontend** | Framework-agnostic benefit unused (all apps are Angular); shadow DOM complicates shared theming; immature Angular integration |
| **iframe-based composition** | Poor UX (no shared navigation state); accessibility challenges; eliminated early |

## Consequences

### Positive
- Independent deployability — Admin can release daily without touching TraderDesk
- Smaller bundle per app — users download only their persona's code
- Security boundaries are physical (separate origins, separate auth scopes)
- Build failures in one app do not block other teams
- Simpler mental model than micro-frontend orchestration

### Negative
- Shared library (`ui-kit`) changes require updating all three apps
- No cross-app client-side navigation (full page load switching between portals)
- Potential design drift if `ui-kit` is not actively maintained

### Mitigations
- `ui-kit` versioned with semver; apps pin to minor version; renovate bot proposes updates
- Cross-app navigation via shared header component with links (acceptable for rare persona switching)
- Storybook instance for `ui-kit` ensures visual consistency across portals
