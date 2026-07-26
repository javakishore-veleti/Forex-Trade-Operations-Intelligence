# Contributing to Forex-Trade-Operations-Intelligence

Thank you for contributing! Please follow these guidelines.

## Branch Naming

Use the following prefixes for branches:

| Prefix | Purpose |
|--------|---------|
| `feat/` | New features |
| `fix/` | Bug fixes |
| `chore/` | Maintenance, dependencies, tooling |
| `docs/` | Documentation changes |
| `refactor/` | Code restructuring (no behavior change) |

Example: `feat/trade-ingest-batch-validation`

## Pull Request Checklist

Before submitting a PR, ensure:

- [ ] All new code compiles / builds without errors
- [ ] Tests pass (`mvn verify` for Middleware, `pytest` for Sidecars)
- [ ] No real credentials, PII, or financial data committed
- [ ] All identifiers use synthetic `FX-` prefix
- [ ] Branch is rebased on latest `main`
- [ ] PR title is concise (< 70 characters)
- [ ] CODEOWNERS have been notified where applicable

## Synthetic Data Policy

All examples, identifiers, and test data **must** use synthetic `FX-` prefixed identifiers (e.g., `FX-000001`). No real financial institution, person, or confidential data may be committed.

## Running the Local Stack

```bash
# Start all infrastructure
./DevOps/Local/docker-all-up.sh

# Check status
./DevOps/Local/all-status.sh

# Stop all infrastructure
./DevOps/Local/docker-all-down.sh
```

## Language Boundaries

- **Java 21 / Spring Boot** — Middleware services only
- **TypeScript / Angular 19** — Portal UIs only
- **Python 3.11+** — Sidecars (detection/embedding only, no business logic)
- **n8n JSON exports** — Agent workflows only

See [docs/adr/0001-monorepo-language-boundaries.md](docs/adr/0001-monorepo-language-boundaries.md) for the full ADR.

## Code Style

- Follow `.editorconfig` settings
- Java: 4-space indentation
- TypeScript/HTML/JSON/YAML: 2-space indentation
- LF line endings, UTF-8, trim trailing whitespace, final newline
