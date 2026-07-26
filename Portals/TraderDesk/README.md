# Trader Desk Portal

Customer-facing trader portal for executing and monitoring FX trades.

## Technology

- **Framework:** Angular 19 (standalone component model)
- **Language:** TypeScript
- **Build Tool:** Angular CLI

## Placeholder Route Paths

The following routes are planned for implementation in a later phase:

| Path | Description |
|------|-------------|
| `/blotter` | Trade blotter view |
| `/new-trade` | New trade entry |
| `/positions` | Open positions overview |
| `/history` | Trade history and audit trail |
| `/market-data` | Live market data feed |

## Development

### Prerequisites

- Node.js (LTS)
- npm

### Install Dependencies

```bash
npm install
```

### Development Server

```bash
ng serve
```

Runs the dev server on port `4201`. Navigate to `http://localhost:4201/`.

```bash
ng serve --port 4201
```

### Production Build

```bash
ng build
```

Build artifacts are output to the `dist/` directory.

## Notes

- This portal uses the Angular standalone component model — no legacy `NgModule` declarations.
- All example identifiers in this project use the synthetic `FX-` prefix (e.g., `FX-000001`).
