# ADR-0034: Agent Deployment — Local-First vs Cloud-Native vs Hybrid

## Status
Accepted

## Context
The 34 agents need to run somewhere. Options: purely local (Docker Compose), purely cloud (managed n8n or EKS-hosted), or a hybrid where development is local and production is cloud.

## Decision
Adopt a **local-first development with cloud promotion** strategy:

### Local (development + testing):
- n8n runs in Docker Compose (`DevOps/Local/agent-platform/`)
- All 34 workflows imported via `import-workflows.sh`
- MCP tools connect to local service containers by service name
- Sidecars POST to localhost webhooks
- Smoke test validates the full chain locally
- Golden-set evals run against local n8n

### Cloud (production):
- n8n deployed on EKS/AKS as a StatefulSet with queue-mode workers
- Workflows imported via CI/CD pipeline (same JSON, different env vars)
- MCP tools connect to service Kubernetes Service DNS names
- Sidecars deployed as CronJobs/Deployments, POST to internal n8n webhooks
- HITL approvals route to Admin Portal (not local UI)
- Horizontal scaling: queue mode allows multiple worker pods

### Promotion workflow:
1. Develop/test agent locally (workflow JSON in Git)
2. Golden-set eval passes (CI gate)
3. PR merged to main
4. CD pipeline imports workflow JSON to cloud n8n instance
5. Shadow eval against production-like data (non-blocking)
6. Activate workflow in production n8n

### Environment parity:
- Same workflow JSON in local and cloud (env vars differentiate endpoints)
- `mcp-servers.json` has local variant (container names) and cloud variant (K8s service names)
- No agent logic changes between environments — only connection config

## Alternatives Considered
- **Cloud-only** (no local n8n, develop directly in cloud) — rejected; too slow for iteration, requires cloud infra always running, costly
- **Local-only** (never deploy agents to cloud) — rejected; doesn't work for production use cases with real latency, scale, and approval routing
- **Managed n8n cloud** (n8n.cloud SaaS) — rejected; loses self-hosted control, data sovereignty concerns for financial operations, can't run in private VPC
- **Separate agent runtimes per environment** (different framework locally vs cloud) — rejected; environment drift causes "works locally, fails in prod"

## Consequences
- Developers iterate fast locally (seconds to test a workflow change)
- Production deployment is automated and repeatable (same JSON, different env vars)
- No environment drift — behavioral parity guaranteed by shared workflow definition
- Cost: cloud n8n instance runs 24/7 (but workers scale to zero when idle with queue mode)
- Trade-off: local Docker Compose can't simulate multi-worker scaling; production-only behavior (parallelism, queue contention) tested via shadow eval
