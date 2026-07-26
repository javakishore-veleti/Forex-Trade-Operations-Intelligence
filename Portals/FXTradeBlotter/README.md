# FX Trade Blotter Portal

Broker-facing trade blotter for managing trade execution and settlement.

## Technology

- **Framework:** Angular 19 (standalone component model)
- **Language:** TypeScript
- **Build Tool:** Angular CLI

## Placeholder Route Paths

The following routes are planned for implementation in a later phase:

| Path | Description |
|------|-------------|
| `/trades` | Active trades blotter |
| `/settlement` | Settlement queue |
| `/matching` | Trade matching status |
| `/exceptions` | Exception management |
| `/reports` | Regulatory reporting |

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

Runs the dev server on the default port `4200`. Navigate to `http://localhost:4200/`.

### Production Build

```bash
ng build
```

Build artifacts are output to the `dist/` directory.

## Notes

- This portal uses the Angular standalone component model — no legacy `NgModule` declarations.
- All example identifiers in this project use the synthetic `FX-` prefix (e.g., `FX-000001`).
