# Admin Portal

Operations and risk administration interface for the FX Trade Operations Intelligence platform.

## Technology

- **Framework:** Angular 19 (standalone component model)
- **Language:** TypeScript
- **Build Tool:** Angular CLI

## Placeholder Route Paths

The following routes are planned for implementation in a later phase:

| Path | Description |
|------|-------------|
| `/dashboard` | Operations overview dashboard |
| `/risk/settings` | Risk parameter configuration |
| `/risk/alerts` | Risk alert management |
| `/users` | User and role management |
| `/system/health` | System health monitoring |

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
