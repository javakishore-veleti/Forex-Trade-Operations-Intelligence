# GitHub Actions Workflows

## Planned Pipelines

The following CI/CD pipelines are planned for future implementation:

| Pipeline | Trigger | Scope |
|----------|---------|-------|
| `middleware-ci` | PR to `main` touching `Middleware/` | Maven build + test |
| `portals-ci` | PR to `main` touching `Portals/` | Angular build + lint |
| `sidecars-ci` | PR to `main` touching `Sidecars/` | Python test + lint |
| `compose-validate` | PR to `main` touching `DevOps/` | `docker compose config` validation |
| `release` | Manual / tag | Build and publish artifacts |

## Current Status

No automated CI workflow files are active yet. Pipelines will be added incrementally as the platform matures.

All workflows will be **manually triggered** initially before enabling automatic triggers.

## Notes

- Existing files (`claude.yml`, `claude-code-review.yml`) are for AI code review tooling integration.
