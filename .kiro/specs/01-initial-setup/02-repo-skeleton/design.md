# Design Document — Repository Skeleton (Initial Setup)

> **Stage 2 of 3** (`requirements.md → design.md → tasks.md`). This document realizes `requirements.md` for the repository skeleton. Requirements are technology-agnostic and reference **Technology Roles**; **design is the concrete stage** — every role resolves here to a pinned product and version, sourced *only* from `01-initial-setup/01-technology-stack` (the registry). This spec is not a microservice, so it inherits no golden-path NFRs — it produces the scaffolding on which every later spec builds. Every design decision below traces to a requirement (see §13).

## 1. Overview

The repository skeleton is the reproducible baseline for the `Forex-Trade-Operations-Intelligence` monorepo: it creates every top-level directory, the parent Maven build descriptor and seven service module scaffolds, three Angular standalone portal scaffolds, four Python detection/embedding sidecar scaffolds, the `Agents/` workflow layout, the nine per-service Docker Compose files under `DevOps/Local/`, the orchestration scripts, the root `package.json`, and the root tooling files (`.gitignore`, `.editorconfig`, `CONTRIBUTING.md`, `.github/CODEOWNERS`, `docs/adr/0001`). No business logic is implemented — each scaffold is an empty-but-valid skeleton that compiles, lints, or starts a container.

**Role → concrete binding** (resolved from the technology-stack registry — the *only* place these products are otherwise named). The requirements.md for this spec predates the agnostic rule and still names products; the bindings below are the authoritative resolution:

| Technology Role | Concrete product / version | Use in this scaffold |
|---|---|---|
| `SERVICE_BUILD_TOOL` | Maven `3.9.x` | `Middleware/pom.xml` parent POM (`packaging=pom`) importing the Spring Boot BOM |
| `SERVICE_LANGUAGE` | Java `21` (LTS) | `maven.compiler.release=21` in the parent POM |
| `SERVICE_FRAMEWORK` | Spring Boot `3.4.x` | BOM import + `spring-boot-maven-plugin` in `pluginManagement` |
| `AGENT_TOOL_PROTOCOL` | Spring AI MCP Server `1.0.x` | BOM import (dependencyManagement only; no endpoints at scaffold time) |
| `FRONTEND_FRAMEWORK` | Angular `19.x` (standalone) | `Portals/{Admin,TraderDesk,FXTradeBlotter}` standalone scaffolds |
| `SIDECAR_LANGUAGE` | Python `>=3.11` | `Sidecars/*` src-layout packages (`requires-python = ">=3.11"`) |
| `SIDECAR_BUILD_BACKEND` | Hatchling (current) | `[build-system]` backend in each sidecar `pyproject.toml` |
| `CONTAINER_RUNTIME` | Docker + Docker Compose (current) | one Compose file per infra service under `DevOps/Local/` |
| `AGENT_PLATFORM` | n8n (pinned container tag) | `Agents/` workflow exports + `DevOps/Local/AGENT_PLATFORM` compose |
| `RELATIONAL_STORE` | PostgreSQL `16.x` | `DevOps/Local/RELATIONAL_STORE` compose |
| `DOCUMENT_STORE` | MongoDB `7.x` | `DevOps/Local/DOCUMENT_STORE` compose |
| `CACHE` | Redis `7.x` | `DevOps/Local/CACHE` compose |
| `GRAPH_STORE` | Neo4j `5.x` | `DevOps/Local/GRAPH_STORE` compose |
| `EVENT_STREAM` | Apache Kafka `3.x` (KRaft) | `DevOps/Local/EVENT_STREAM` compose |
| `OBSERVABILITY_METRICS` | Prometheus `2.x` + Grafana `11.x` | `DevOps/Local/OBSERVABILITY_METRICS` + metrics-visualization compose |
| `OBSERVABILITY_LOGGING` | Elasticsearch + Logstash + Kibana `8.x` | `DevOps/Local/OBSERVABILITY_LOGGING` compose (3 services) |
| `UNIT_TEST_FRAMEWORK` / `WEB_LAYER_TEST` / `INTEGRATION_TEST_HARNESS` / `PROPERTY_TEST` | JUnit 5 / Spring MockMvc / Testcontainers / jqwik | version-pinned in parent POM `dependencyManagement` |

Concrete image tags for Compose are pinned in §6; they are chosen to match the registry version lines and are **never** `latest`.

## 2. Repository root structure (Req 1)

The scaffold produces exactly the "Repo Root Structure" of `MASTER-PLAN.md`. Every directory carries a `README.md` (Req 1.3); a directory without one is treated as an invalid scaffold (Req 1.5).

```
Forex-Trade-Operations-Intelligence/
├── Middleware/                 # SERVICE_BUILD_TOOL parent POM + 7 SERVICE_FRAMEWORK modules
│   ├── pom.xml                 # Parent_Build_Descriptor (packaging=pom) — §3
│   ├── README.md
│   ├── shared-domain-contracts/       # library artifact (no repackaging) — §4
│   ├── trade-ingest-service/
│   ├── trade-lifecycle-service/
│   ├── risk-calculation-service/
│   ├── eod-processing-service/
│   ├── business-calendar-service/
│   └── state-reconciliation-service/
├── Portals/                    # 3 FRONTEND_FRAMEWORK standalone apps — §4
│   ├── Admin/
│   ├── TraderDesk/
│   └── FXTradeBlotter/
├── Agents/                     # AGENT_PLATFORM workflow JSON exports ONLY
│   ├── workflows/
│   │   ├── supervisor/         # supervisor-trade-operations.workflow.json
│   │   ├── specialized/
│   │   └── utilities/
│   ├── credentials/            # README only — no credential values (Req 13.5)
│   └── README.md
├── Sidecars/                   # 4 SIDECAR_LANGUAGE detection/embedding packages — §4
│   ├── kpi-anomaly-detector/
│   ├── dlq-cluster-analyzer/
│   ├── capacity-forecast-model/
│   └── log-normalizer/
├── DevOps/
│   └── Local/                  # 9 CONTAINER_RUNTIME compose files + 3 scripts — §6
├── docs/
│   ├── adr/                    # README + 0001-monorepo-language-boundaries.md — §7
│   └── diagrams/
├── .github/
│   ├── workflows/              # README placeholder only (Req 10)
│   └── CODEOWNERS
├── scripts/
├── package.json                # Root_Package_JSON: start/stop/status/install — §6
├── .gitignore  .editorconfig  CONTRIBUTING.md  README.md
```

The root `README.md` (Req 1.2) states the platform purpose, the top-level directory roles, a link to the architectural-constraints doc, and the synthetic-data policy (all examples use `FX-` identifiers, e.g. `FX-000001`).

## 3. Parent Maven POM design (Req 2)

`Middleware/pom.xml` is the `Parent_Build_Descriptor`. It carries **no application code** — its job is to centralize versions and plugin config so no child module re-declares them (Req 2.2, Req 2.6).

**Coordinates & packaging**
```
groupId    = com.fxtradeops
artifactId = fxtradeops-parent
version    = 0.1.0-SNAPSHOT
packaging  = pom
```

**Properties** — the single place the pinned `SERVICE_LANGUAGE` version lives:
```
maven.compiler.release = 21
project.build.sourceEncoding = UTF-8
spring-boot.version   = 3.4.x      (resolved concrete patch pinned here)
spring-ai.version     = 1.0.x      (AGENT_TOOL_PROTOCOL BOM)
testcontainers.version = <current> (INTEGRATION_TEST_HARNESS)
jqwik.version         = <current>  (PROPERTY_TEST)
```

**`<dependencyManagement>`** — imports BOMs (`scope=import`, `type=pom`) so modules add starters without versions (Req 2.2):
- Spring Boot BOM (`spring-boot-dependencies`) — `SERVICE_FRAMEWORK`; transitively pins JUnit 5 (`UNIT_TEST_FRAMEWORK`) and Spring MockMvc (`WEB_LAYER_TEST`).
- Spring AI BOM (`spring-ai-bom`) — `AGENT_TOOL_PROTOCOL` (managed now, consumed in Phase 6).
- Testcontainers BOM — `INTEGRATION_TEST_HARNESS`.
- jqwik — `PROPERTY_TEST`, pinned as a managed dependency (no BOM).

**`<pluginManagement>`** — shared plugin config, versions resolved from the registry:
- `maven-compiler-plugin` → `<release>${maven.compiler.release}</release>` (= 21) for source+bytecode compatibility across all modules (Req 2.3).
- `spring-boot-maven-plugin` → managed here; runnable services opt into the `repackage` goal, `shared-domain-contracts` does **not** (§4, Req 3.3).
- `maven-surefire-plugin` → runs the context-load tests (Req 3.5/3.6).

**`<modules>`** — lists all seven Phase-0 modules (Req 2.1, Req 3.1):
```
shared-domain-contracts, trade-ingest-service, trade-lifecycle-service,
risk-calculation-service, eod-processing-service, business-calendar-service,
state-reconciliation-service
```
Adding a module later requires appending it here before the scaffold is complete (Req 2.4). `Middleware/README.md` documents the parent structure, the Java-21 requirement, how to add a module, and the initial module list (Req 2.5).

## 4. Per-scaffold module conventions (Req 3, 4, 6)

### 4.1 Service module scaffold (`Middleware/<service>/`) — Req 3

Every runnable `Service_Module` follows one template:
```
<service>/
├── pom.xml                     # <parent> → fxtradeops-parent (relativePath ../pom.xml)
├── src/main/java/com/fxtradeops/<pkg>/<Name>Application.java   # @SpringBootApplication
├── src/main/resources/application.yml                          # spring.application.name=<service> (kebab)
└── src/test/java/.../<Name>ApplicationTests.java               # context-load test (Req 3.2/3.6)
```
- Child POM declares its parent as `fxtradeops-parent`; if it does not, the scaffold is non-conforming (Req 2.6).
- Runnable services enable `spring-boot-maven-plugin` repackage.
- **`shared-domain-contracts`** is the exception (Req 3.3/3.4): a plain library artifact — no `@SpringBootApplication`, no repackaging, compile-scope consumable. It hosts the `MCP_Tool_Contract` DTOs (`requestId`, `businessEntity`, `status`, `facts`, `violations`, `permittedActions`, `evidence`, `dataClassification`, `expiresAt`) with field-level docs, defined in detail by `02-microservices/01-shared-domain-contracts`; this scaffold only creates the module shell. Its future test fixtures use synthetic ids only (`requestId="req-00001"`, `entityId="FX-000001"`) (Req 13.4).
- **`state-reconciliation-service`** is the only Phase-0 scaffold that declares the `EVENT_STREAM` (Spring Kafka) dependency, because it reads the stream at the reconciliation layer; the other scaffolds omit it until a feature spec justifies it (Req 3.7).

### 4.2 Portal scaffold (`Portals/<App>/`) — Req 4

Each of `Admin`, `TraderDesk`, `FXTradeBlotter` is an independent Angular 19 standalone app:
```
<App>/
├── package.json          # @angular/* pinned at the SAME major (19.x) across all three (Req 4.3/4.8)
├── angular.json          # framework build config
├── tsconfig.json
├── .editorconfig         # inherits root (Req 4.2)
├── src/index.html
├── src/main.ts           # bootstrapApplication(AppComponent) — standalone, no NgModule (Req 4.4)
├── src/app/app.component.ts   # standalone: true
└── README.md             # role + placeholder route paths (Req 4.5/4.6/4.7)
```
`ng build` must produce a production bundle with no legacy module declarations. Pinning the same Angular major across all three prevents version drift; a mismatch fails the CI drift check (Req 4.8).

### 4.3 Sidecar scaffold (`Sidecars/<pkg>/`) — Req 6

Each of `kpi-anomaly-detector`, `dlq-cluster-analyzer`, `capacity-forecast-model`, `log-normalizer` is a Python `>=3.11` src-layout package built with Hatchling:
```
<pkg>/
├── pyproject.toml        # [project] name/version/requires-python=">=3.11"; [build-system]=hatchling (Req 6.2/6.6)
├── src/<package_name>/__init__.py   # exposes __version__
├── tests/test_smoke.py   # imports package, asserts __version__ non-empty (Req 6.4)
├── Dockerfile            # python:3.11-slim base, installs package, sets entrypoint (Req 6.2/6.5)
└── README.md             # detection/embedding function, inputs/outputs, build+run command
```
`Sidecars/README.md` states the boundary: this directory is ONLY statistical detection/embedding — no business logic, no trade processing, no agent orchestration, no `SERVICE_FRAMEWORK` replacement; each entrypoint emits a compact anomaly envelope compatible with `MCP_Tool_Contract` when a threshold trips (Req 6.3). The `kpi-anomaly-detector` README includes an example output JSON using synthetic `FX-` ids (Req 6.7, 13.6).

### 4.4 Agents layout (Req 5)

`Agents/` holds `AGENT_PLATFORM` workflow exports only. Tree: `workflows/{supervisor,specialized,utilities}/` + `credentials/`. `workflows/README.md` defines the naming convention `{category}-{short-description}.workflow.json`. A placeholder `workflows/supervisor/supervisor-trade-operations.workflow.json` carries a minimal valid n8n skeleton (`name`, `nodes`, `connections`, `settings`) so the import mechanism is verifiable (Req 5.4). `Agents/README.md` states all agents are n8n workflow exports (no Python agent scripts, no FastAPI servers, no third-party agent framework code) and that JSON is exported from the platform, not hand-authored (Req 5.5). `credentials/README.md` explains no credential values live in the repo and documents the env-var/secret-manager mechanism with synthetic examples (Req 1.4, 13.5).

## 5. DevOps/Local compose set + orchestration + root package.json (Req 7, 8, 9)

### 5.1 Compose files (Req 7)

`DevOps/Local/` contains one compose file per infra role, each in a named subdirectory. Every file pins an explicit image tag (never `latest`, Req 7.7), a named data volume, a bridge network named `fxops-{role-short-name}-net` (Req 7.6), and a role-appropriate `healthcheck` (Req 7.2).

| Subdirectory | Product / **pinned tag** | Network | Default port | Healthcheck |
|---|---|---|---|---|
| `RELATIONAL_STORE/` | PostgreSQL **`postgres:16.4`** | `fxops-relational-net` | 5432 | `pg_isready` |
| `EVENT_STREAM/` | Kafka KRaft **`apache/kafka:3.8.0`** (broker + controller, single-node KRaft) | `fxops-event-net` | 9092 | broker API-versions probe (Req 7.4) |
| `CACHE/` | Redis **`redis:7.4`** | `fxops-cache-net` | 6379 | `redis-cli ping` |
| `DOCUMENT_STORE/` | MongoDB **`mongo:7.0`** | `fxops-document-net` | 27017 | `mongosh ping` |
| `GRAPH_STORE/` | Neo4j **`neo4j:5.23`** | `fxops-graph-net` | 7474/7687 | cypher-shell probe |
| `OBSERVABILITY_LOGGING/` | ELK **`elasticsearch:8.15.0`** + **`logstash:8.15.0`** + **`kibana:8.15.0`** (3 services) | `fxops-logging-net` | 9200/5044/5601 | ES cluster health; Logstash & Kibana `depends_on: {elasticsearch: condition: service_healthy}` (Req 7.3) |
| `OBSERVABILITY_METRICS/` | Prometheus **`prom/prometheus:v2.54.0`** | `fxops-metrics-net` | 9090 | `/-/healthy` |
| `metrics-visualization/` | Grafana **`grafana/grafana:11.1.0`** (dashboards for OBSERVABILITY_METRICS) | `fxops-metricsviz-net` | 3000 | `/api/health` |
| `AGENT_PLATFORM/` | n8n **`n8nio/n8n:1.55.0`** | `fxops-agent-net` | 5678 | `/healthz`; volume-mounted `~/.n8n` data dir (Req 7.5) |

`DevOps/Local/README.md` lists all nine roles, default ports, the `fxops-*-net` naming convention, and how to drive the orchestration scripts (Req 7.8).

### 5.2 Orchestration scripts (Req 8)

Three `Developer_Script`s live in `DevOps/Local/`, each with a header comment (purpose, args, example usage with synthetic service names), LF line endings, and the executable bit set in Git (Req 8.7/8.8):

- **`docker-all-up.sh`** — no args: `docker compose up -d` per subdirectory in dependency order `RELATIONAL_STORE → EVENT_STREAM → CACHE → DOCUMENT_STORE → GRAPH_STORE → OBSERVABILITY_LOGGING → OBSERVABILITY_METRICS → metrics-visualization → AGENT_PLATFORM` (Req 8.2). With role-name args, start only those, honoring the same relative order (Req 8.3).
- **`docker-all-down.sh`** — no args: `docker compose down` in the exact reverse order (Req 8.4); with args, stop only the named ones (Req 8.5).
- **`all-status.sh`** — `docker compose ps` per directory, printing a consolidated summary of role, container status, exposed ports (Req 8.6).

All three validate that each referenced subdirectory exists; a missing one prints to stderr and exits non-zero (Req 8.9).

### 5.3 Root package.json (Req 9)

`Repository_Root/package.json` is `"private": true` (Req 9.7), declares no application dependencies (Req 9.6), and exposes four scripts that delegate to the DevOps scripts and pass through their exit code (Req 9.8):
```json
{
  "private": true,
  "scripts": {
    "start":   "DevOps/Local/docker-all-up.sh",
    "stop":    "DevOps/Local/docker-all-down.sh",
    "status":  "DevOps/Local/all-status.sh",
    "install": "cd Portals/Admin && npm install && cd ../TraderDesk && npm install && cd ../FXTradeBlotter && npm install"
  }
}
```
`install` runs the Angular dependency install for each portal in order `Admin → TraderDesk → FXTradeBlotter` (Req 9.5).

## 6. Tooling files (Req 10, 11, 12)

- **`.gitignore`** (Req 12.1/12.2/12.6) — excludes Maven (`target/`), IDE metadata (`.idea/`, `.vscode/`, `*.iml`), Angular build/cache (`dist/`, `.angular/`, `node_modules/`), Python (`__pycache__/`, `*.egg-info/`, `.venv/`, `build/`), Compose overrides (`docker-compose.override.yml`), and secrets (`.env`, `*.env`, `*.pem`, `*.key`, `secrets/`). It must **not** ignore the versioned Compose files themselves.
- **`.editorconfig`** (Req 12.3) — `root=true`; `indent_style=space`; `indent_size=4` for Java + XML; `indent_size=2` for TS/HTML/JSON/YAML; `end_of_line=lf`; `charset=utf-8`; `trim_trailing_whitespace=true`; `insert_final_newline=true`.
- **`CONTRIBUTING.md`** (Req 12.4) — branch prefixes `feature/`, `fix/`, `docs/`; PR checklist (all service scaffolds compile, all portals build, all sidecar suites pass); synthetic-data policy; how to run the full local stack.
- **`.github/CODEOWNERS`** (Req 10.5, 12.5) — `Middleware/`→`@fxops/platform-java`, `Portals/`→`@fxops/platform-frontend`, `Agents/`→`@fxops/platform-agents`, `Sidecars/`→`@fxops/platform-python`, `DevOps/`→`@fxops/platform-devops`, `docs/`→`@fxops/platform-architecture`.
- **`.github/workflows/README.md`** (Req 10) — no CI files yet; planned pipelines (Maven multi-module build, Angular lint+build, Python test + Docker build, n8n workflow lint, compose image-tag validation); all manually triggered.
- **`docs/adr/README.md` + `docs/adr/0001-monorepo-language-boundaries.md`** (Req 11.2/11.3) — ADR README states MADR/Nygard format, `NNNN-{short-title}.md` naming, context/decision/consequences requirement. ADR-0001 records the language-boundary decision (§7). `docs/diagrams/README.md` (Req 11.4) and `docs/README.md` TOC (Req 11.5) complete the docs tree.

## 7. ADR-0001 — Monorepo language boundaries (Req 11.3)

- **Context:** a polyglot monorepo (Java services, Angular portals, Python sidecars, n8n agents) risks logic leaking across tiers.
- **Decision:** microservices/business logic use `SERVICE_LANGUAGE`/`SERVICE_FRAMEWORK` (Java 21 / Spring Boot) **only**; AI agents are `AGENT_PLATFORM` (n8n) workflow exports **only**; `SIDECAR_LANGUAGE` (Python) is restricted to statistical detection/embedding sidecars — never business logic or agent orchestration.
- **Consequences:** exact arithmetic, transactional consistency, and high-volume consumption stay in Java; sidecars emit compact anomaly envelopes; agents orchestrate via `AGENT_TOOL_PROTOCOL`.
- Uses only fictional service names and synthetic `FX-` identifiers; references no real employer, client, or production system (Req 13.7).

## 8. Testing / validation strategy (Req 3.5, 4.4, 6.4)

- **Parent POM well-formed:** `mvn -N validate` at `Middleware/` (non-recursive) — the parent resolves BOM imports and plugin management without error.
- **Modules compile + context-load:** `mvn -f Middleware/pom.xml verify` compiles all seven modules and runs each context-load test (Req 3.5); a module missing its test fails the test phase (Req 3.6).
- **Portals build:** `ng build` in each portal produces a prod bundle with no legacy module declarations (Req 4.4).
- **Sidecars test:** the Python test runner in each sidecar runs the smoke test green (Req 6.4); `docker build` produces the image (Req 6.5).
- **Compose valid:** `docker compose config` in each `DevOps/Local/*` subdirectory parses the file and confirms no `latest` tag, a named volume, an `fxops-*-net` bridge network, and a healthcheck (Req 7.2/7.7).
- **Scripts:** `docker-all-up.sh`/`-down.sh`/`all-status.sh` are executable, LF, and error non-zero on a missing subdirectory (Req 8.9).

## 9. Design decisions (ADR-lite)

- **Parent POM as version single-source:** all versions (Java 21, Spring Boot BOM, Testcontainers, jqwik) live in one `dependencyManagement`/`properties` block so modules never re-declare versions — mirrors the technology-stack single-point-of-change principle.
- **Per-service compose files, not one monolith:** developers start only what they need; each file is self-contained with its own `fxops-*-net` network, so composing subsets never collides.
- **Pinned image tags, never `latest`:** reproducible local builds; tags are chosen to match the registry version lines and are the enforcement target of the compose image-tag CI check.
- **`shared-domain-contracts` as a library, not an app:** it is a compile-scope dependency of every service, so it must not be repackaged into a runnable jar.
- **Angular standalone, single pinned major across portals:** avoids `NgModule` boilerplate and prevents cross-portal version drift.

## 10. Requirement → design traceability

| Requirement | Satisfied by |
|---|---|
| Req 1 Top-level layout + READMEs | §2 |
| Req 2 Parent build descriptor | §3 |
| Req 3 Service scaffolds | §3 (modules), §4.1 |
| Req 4 Portal scaffolds | §4.2 |
| Req 5 Agents layout | §4.4 |
| Req 6 Sidecar packages | §4.3 |
| Req 7 Per-service compose | §5.1 |
| Req 8 Orchestration scripts | §5.2 |
| Req 9 Root package.json | §5.3 |
| Req 10 .github placeholder + CODEOWNERS | §6 |
| Req 11 docs/ + ADR-0001 | §6, §7 |
| Req 12 Root tooling files | §6 |
| Req 13 Synthetic-data safeguard | §1, §4.1, §4.3, §7 (applied throughout) |
