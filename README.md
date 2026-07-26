# Forex-Trade-Operations-Intelligence

Forex-Trade-Operations-Intelligence is a spec-driven, publicly available reference implementation of a runtime-intelligence platform for foreign-exchange trade operations.

## Top-Level Directory Structure

| Directory | Role |
|-----------|------|
| `Middleware/` | Java 21 / Spring Boot microservices — parent Maven POM and all service modules |
| `Portals/` | Three Angular 19 standalone portal applications (Admin, TraderDesk, FXTradeBlotter) |
| `Agents/` | n8n workflow JSON exports only — supervisor, specialized, and utility agent workflows |
| `Sidecars/` | Python detection and embedding sidecar packages (statistical analysis, not business logic) |
| `DevOps/` | Infrastructure-as-code for local and future cloud environments |
| `docs/` | Architecture Decision Records (ADRs), diagrams, and design documentation |
| `.github/` | GitHub configuration, CODEOWNERS, and CI workflow placeholders |
| `scripts/` | Utility scripts for the monorepo |

## Architectural Constraints

See [docs/adr/0001-monorepo-language-boundaries.md](docs/adr/0001-monorepo-language-boundaries.md) for the foundational decision on language and tier boundaries across the platform.

## Synthetic Data Policy

All examples, identifiers, and test data in this repository use synthetic `FX-` prefixed identifiers (e.g., FX-000001). No real financial institution, person, or confidential data is committed.
