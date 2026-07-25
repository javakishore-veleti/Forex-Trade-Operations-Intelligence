# Tasks — Repository Skeleton (Initial Setup)

> **Stage 3 of 3** (`requirements.md → design.md → tasks.md`). Derived from `design.md`. Execute
> top-to-bottom; each task is atomic, maps to specific files, and is independently verifiable.
> This is a **living checklist** — mark `[x]` as each task is completed and verified. Tags in
> parentheses trace to design sections (§) and requirements (Req). All example identifiers are
> synthetic `FX-` ids; all organizations are fictional; no real credentials (Req 13).

## 0. Repository root layout
- [x] 0.1 Create top-level directories `Middleware/`, `Portals/`, `Agents/`, `Sidecars/`, `DevOps/Local/`, `docs/`, `.github/`, `scripts/`. (§2, Req 1.1)
- [x] 0.2 Root `README.md`: platform purpose, top-level directory roles, link to architectural-constraints doc, synthetic-`FX-`-data statement. **Verify:** every top-level directory has a `README.md`. (§2, Req 1.2/1.3/1.5)

## 1. Parent Maven POM (Req 2)
- [x] 1.1 `Middleware/pom.xml` — `com.fxtradeops:fxtradeops-parent:0.1.0-SNAPSHOT`, `packaging=pom`, `properties` with `maven.compiler.release=21` (SERVICE_LANGUAGE resolved). (§3)
- [x] 1.2 `<dependencyManagement>` importing the Spring Boot BOM + Spring AI (AGENT_TOOL_PROTOCOL) BOM + Testcontainers BOM, and pinning jqwik (PROPERTY_TEST). (§3, Req 2.2)
- [x] 1.3 `<pluginManagement>`: `maven-compiler-plugin` (`release=21`), `spring-boot-maven-plugin`, `maven-surefire-plugin`. (§3, Req 2.3)
- [x] 1.4 `<modules>` listing all seven Phase-0 modules. (§3, Req 2.1)
- [x] 1.5 `Middleware/README.md`: parent structure, Java-21 requirement, how to add a module, initial module list. (§3, Req 2.5) **Verify:** `mvn -N validate` at `Middleware/` succeeds.

## 2. Service module scaffolds (Req 3)
- [x] 2.1 `shared-domain-contracts/` — library artifact (parent `fxtradeops-parent`, no `@SpringBootApplication`, no repackaging), shell for `MCP_Tool_Contract` DTOs. (§4.1, Req 3.3/3.4)
- [x] 2.2 Six runnable service scaffolds `trade-ingest-service`, `trade-lifecycle-service`, `risk-calculation-service`, `eod-processing-service`, `business-calendar-service`, `state-reconciliation-service`: child `pom.xml`→parent, `<Name>Application` (`@SpringBootApplication`), `application.yml` (`spring.application.name` kebab), context-load test. (§4.1, Req 3.1/3.2)
- [x] 2.3 `state-reconciliation-service` declares the EVENT_STREAM (Spring Kafka) dependency; the other five do not. (§4.1, Req 3.7)
- [x] 2.4 Per-service `README.md` for each module. (Req 1.3) **Verify:** `mvn -f Middleware/pom.xml verify` — all seven compile, all context-load tests green.

## 3. Portal scaffolds (Req 4)
- [x] 3.1 `Portals/{Admin,TraderDesk,FXTradeBlotter}` Angular 19 standalone scaffolds: `package.json`, `angular.json`, `tsconfig.json`, `.editorconfig` (inherits root), `src/index.html`, `src/main.ts` (`bootstrapApplication`), standalone `AppComponent`. (§4.2, Req 4.1/4.2/4.4)
- [x] 3.2 Pin `@angular/*` at the same major (19.x) across all three portals. (§4.2, Req 4.3/4.8)
- [-] 3.3 Per-portal `README.md`: Admin=ops/risk admin, TraderDesk=customer trader portal, FXTradeBlotter=broker trade blotter, each listing placeholder route paths. (Req 4.5/4.6/4.7) **Verify:** `ng build` in each portal produces a prod bundle, no legacy module declarations.

## 4. Sidecar scaffolds (Req 6)
- [-] 4.1 `Sidecars/{kpi-anomaly-detector,dlq-cluster-analyzer,capacity-forecast-model,log-normalizer}`: `pyproject.toml` (`[project]` + `requires-python=">=3.11"` + `[build-system]`=hatchling), `src/<pkg>/__init__.py` (`__version__`), `tests/test_smoke.py`, `Dockerfile` (`python:3.11-slim`), `README.md`. (§4.3, Req 6.2)
- [~] 4.2 `kpi-anomaly-detector/README.md` includes an example output JSON using synthetic `FX-` ids. (§4.3, Req 6.7/13.6)
- [~] 4.3 `Sidecars/README.md`: detection/embedding-only boundary + MCP_Tool_Contract-compatible envelope note. (§4.3, Req 6.3) **Verify:** Python test runner green in each sidecar; `docker build` succeeds for each.

## 5. Agents layout (Req 5)
- [~] 5.1 `Agents/workflows/{supervisor,specialized,utilities}/` + `Agents/credentials/`. (§4.4, Req 5.1)
- [~] 5.2 `workflows/README.md` naming convention + placeholder `supervisor/supervisor-trade-operations.workflow.json` (minimal valid n8n skeleton: `name`, `nodes`, `connections`, `settings`). (§4.4, Req 5.2/5.4)
- [~] 5.3 `Agents/README.md` (n8n-exports-only boundary) + `credentials/README.md` (no credential values; synthetic examples). (§4.4, Req 5.5/1.4/13.5)

## 6. DevOps/Local compose files (Req 7)
- [~] 6.1 Compose files for `RELATIONAL_STORE` (postgres:16.4), `CACHE` (redis:7.4), `DOCUMENT_STORE` (mongo:7.0), `GRAPH_STORE` (neo4j:5.23), `AGENT_PLATFORM` (n8nio/n8n:1.55.0) — each pinned tag, named volume, `fxops-{role}-net` bridge network, healthcheck. (§5.1, Req 7.2/7.5/7.6/7.7)
- [~] 6.2 `EVENT_STREAM` compose (apache/kafka:3.8.0, single-node KRaft broker+controller). (§5.1, Req 7.4)
- [~] 6.3 `OBSERVABILITY_LOGGING` compose: elasticsearch+logstash+kibana 8.15.0, Logstash & Kibana `depends_on` ES `service_healthy`. (§5.1, Req 7.3)
- [~] 6.4 `OBSERVABILITY_METRICS` (prom/prometheus:v2.54.0) + `metrics-visualization` (grafana/grafana:11.1.0) compose files. (§5.1)
- [~] 6.5 `DevOps/Local/README.md`: nine roles, default ports, `fxops-*-net` convention, script usage. (§5.1, Req 7.8) **Verify:** `docker compose config` in each `DevOps/Local/*` subdirectory parses, and confirms no `latest` tag.

## 7. Orchestration scripts + root package.json (Req 8, 9)
- [~] 7.1 `DevOps/Local/docker-all-up.sh` (dependency order) + `docker-all-down.sh` (reverse) + `all-status.sh`, each with header comment, LF endings, executable bit, missing-dir → stderr + non-zero exit. (§5.2, Req 8.1–8.9)
- [~] 7.2 Root `package.json`: `"private": true`, scripts `start`/`stop`/`status`/`install` delegating to the DevOps scripts (install loops portals `Admin→TraderDesk→FXTradeBlotter`), no app dependencies. (§5.3, Req 9.1–9.8)

## 8. Tooling files (Req 10, 11, 12)
- [~] 8.1 `.gitignore` (Maven/IDE/Angular/Python/compose-override/secrets; keeps compose files tracked). (§6, Req 12.1/12.2/12.6)
- [~] 8.2 `.editorconfig` (`root=true`; 4-space Java/XML, 2-space TS/HTML/JSON/YAML; lf; utf-8; trim; final newline). (§6, Req 12.3)
- [~] 8.3 `CONTRIBUTING.md` (branch prefixes, PR checklist, synthetic-data policy, run-local-stack). (§6, Req 12.4)
- [~] 8.4 `.github/CODEOWNERS` (six directory→team mappings) + `.github/workflows/README.md` (planned pipelines, manually triggered, no CI files yet). (§6, Req 10.2/10.3/10.5/12.5)
- [~] 8.5 `docs/adr/README.md`, `docs/diagrams/README.md`, `docs/README.md` TOC. (§6, Req 11.2/11.4/11.5)

## 9. ADR-0001 (Req 11.3)
- [~] 9.1 `docs/adr/0001-monorepo-language-boundaries.md`: context/decision/consequences of Java-services / n8n-agents / Python-sidecars-only boundary; fictional service names + synthetic `FX-` ids only. (§7, Req 11.3/13.7)

## 10. Verification & tracking
- [~] 10.1 Full validation: `mvn -N validate` (parent) + `mvn -f Middleware/pom.xml verify` + `ng build` per portal + Python tests per sidecar + `docker compose config` per `DevOps/Local/*` — all green.
- [~] 10.2 Update `MASTER-PLAN.md`: mark `01-initial-setup/02-repo-skeleton` design+tasks complete (clear the "needs agnostic rewrite" flag).
- [~] 10.3 Commit via normal commit / `scripts/commit-specs.sh`.

---
**Completion:** 14 / 36 tasks. Update this line as tasks are ticked.
