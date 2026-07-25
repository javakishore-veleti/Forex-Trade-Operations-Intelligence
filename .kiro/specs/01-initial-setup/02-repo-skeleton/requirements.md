# Requirements Document

## Introduction

This feature establishes the complete initial repository structure for the
**Forex-Trade-Operations-Intelligence** monorepo. The monorepo is a spec-driven,
publicly available reference implementation of a runtime-intelligence platform
for foreign-exchange trade operations. The initial setup scaffolds every top-level
directory, service skeleton, portal app, n8n workflow layout, Python sidecar
packages, local infrastructure compose files, orchestration scripts, and root
developer-tooling files so that all subsequent build phases start from a coherent,
reproducible baseline.

No business logic is implemented at this stage. Every scaffold is an empty-but-valid
project skeleton that compiles, passes a lint check, or starts a container, as
appropriate to its technology. All example data and identifiers used in comments,
READMEs, and configuration must follow the synthetic-data policy defined in
Requirement 13.

---

## Glossary

- **Monorepo**: The single Git repository `Forex-Trade-Operations-Intelligence/`
  that contains all components of the platform.
- **Repository_Root**: The top-level directory of the Monorepo.
- **Middleware**: The `Middleware/` subdirectory; contains all Spring Boot
  (Java/Maven) microservices.
- **Portals**: The `Portals/` subdirectory; contains the three standalone Angular
  frontend applications.
- **Agents**: The `Agents/` subdirectory; contains n8n workflow JSON exports and
  credential documentation only.
- **Sidecars**: The `Sidecars/` subdirectory; contains Python detection and
  embedding packages.
- **DevOps**: The `DevOps/` subdirectory; contains all infrastructure-as-code for
  local and future cloud environments.
- **DevOps_Local**: The `DevOps/Local/` subdirectory; contains per-service
  `docker-compose.yaml` files and orchestration shell scripts.
- **Spring_Boot_Service**: Any Maven module under `Middleware/` that produces a
  runnable Spring Boot JAR.
- **Angular_App**: Any standalone Angular application under `Portals/` that has
  its own `package.json` and `angular.json`.
- **n8n_Workflow**: A JSON-formatted workflow export from n8n placed under
  `Agents/workflows/`.
- **Sidecar**: Any Python package under `Sidecars/` that provides statistical
  detection or embedding functionality.
- **Parent_POM**: The Maven `pom.xml` at `Middleware/pom.xml` that declares all
  child modules and shared dependency management.
- **Scaffold**: An empty-but-valid skeleton that satisfies the toolchain's minimum
  structural requirements (e.g., a Spring Boot service with `Application.java` and
  a `pom.xml` referencing the Parent_POM).
- **Synthetic_Identifier**: A placeholder trade, counterparty, rule, or entity
  identifier that does not correspond to any real financial institution, person, or
  system. All trade identifiers MUST use the `FX-` prefix (e.g., `FX-000001`).
- **Developer_Script**: An executable shell script under `DevOps/Local/` that
  wraps Docker Compose commands for local developer use.
- **Root_Package_JSON**: The `package.json` at `Repository_Root/package.json` that
  exposes developer convenience commands (`start`, `stop`, `status`, `install`).
- **CODEOWNERS**: The `.github/CODEOWNERS` file that maps directory prefixes to
  responsible teams.
- **ADR**: Architecture Decision Record; a Markdown file stored under `docs/adr/`.
- **MCP_Tool_Contract**: The agent envelope schema (`requestId`, `businessEntity`,
  `status`, `facts`, `violations`, `permittedActions`, `evidence`,
  `dataClassification`, `expiresAt`) defined in the shared DTO library and
  referenced throughout CLAUDE.md and the PRD.

---

## Requirements

### Requirement 1: Top-Level Directory Layout and README Files

**User Story:** As a contributor onboarding to the monorepo, I want every major
top-level directory to exist and contain a `README.md` that explains its purpose
and conventions, so that I can navigate the repository without needing external
documentation.

#### Acceptance Criteria

1. THE Repository_Root SHALL contain the following directories:
   `Middleware/`, `Portals/`, `Agents/`, `Sidecars/`, `DevOps/`, `docs/`,
   `.github/`, `scripts/`.

2. THE Repository_Root SHALL contain a `README.md` that describes the overall
   platform purpose, lists the top-level directories and their roles, references
   the CLAUDE.md architectural constraints, and states that all examples use
   Synthetic_Identifiers.

3. WHEN a top-level directory is created, THE Repository_Root SHALL ensure that
   directory contains a `README.md` explaining its purpose, the technology it
   contains, and the conventions contributors must follow within it.

4. THE `Agents/credentials/README.md` file SHALL explain that no credential values
   are stored in the repository, describe the expected secret-manager or
   environment-variable mechanism, and provide an example using Synthetic_Identifier
   values only.

5. IF a directory exists but lacks a `README.md`, THEN THE Repository_Root SHALL
   treat that directory as incomplete and the scaffold as invalid.

---

### Requirement 2: Middleware — Maven Multi-Module Structure

**User Story:** As a Java developer building a new Spring Boot microservice, I want
a Parent POM and a consistent per-module directory convention, so that I can add a
new service by following a repeatable template without manually assembling
Maven boilerplate.

#### Acceptance Criteria

1. THE `Middleware/` directory SHALL contain a `Parent_POM` at
   `Middleware/pom.xml` that declares `<packaging>pom</packaging>` and lists each
   Spring_Boot_Service as a `<module>` entry.

2. THE Parent_POM SHALL declare a `<dependencyManagement>` section that pins the
   Spring Boot BOM, Spring AI BOM, and common testing libraries to explicit
   version numbers.

3. THE Parent_POM SHALL declare shared `<build>` plugin configuration including
   the `spring-boot-maven-plugin` and `maven-compiler-plugin` targeting Java 21
   source and target compatibility.

4. WHEN a new Spring_Boot_Service module is added to `Middleware/`, THE Parent_POM
   SHALL be updated to include that module in its `<modules>` list before the
   scaffold is considered complete.

5. THE `Middleware/` directory SHALL contain a `README.md` that documents the
   Parent_POM structure, Java version requirement, how to add a new module, and
   which modules exist as of the initial setup.

6. IF a Spring_Boot_Service `pom.xml` does not declare `<parent>` pointing to the
   Parent_POM, THEN THE Scaffold SHALL be treated as non-conforming and must be
   corrected before merging.

---

### Requirement 3: Phase-0 Spring Boot Service Scaffolds

**User Story:** As a developer beginning implementation of a microservice, I want
each Phase-0 service to start as a compilable, runnable Spring Boot skeleton, so
that I can wire in business logic incrementally without restructuring the module
layout.

#### Acceptance Criteria

1. THE `Middleware/` directory SHALL contain the following seven Spring_Boot_Service
   modules as subdirectories:
   `shared-mcp-contracts/`, `mcp-tool-gateway/`, `trade-lifecycle-service/`,
   `state-reconciliation-service/`, `risk-calculation-service/`,
   `business-calendar-service/`, `event-sequence-processor/`.

2. EACH Spring_Boot_Service module SHALL contain:
   - A `pom.xml` referencing the Parent_POM;
   - A main application class at
     `src/main/java/com/fxtradeops/{module-name-camel-case}/Application.java`
     annotated with `@SpringBootApplication`;
   - An `application.yml` at `src/main/resources/application.yml` that sets
     `spring.application.name` to the service name using kebab-case;
   - A placeholder `src/test/java/` directory containing a context-load test class
     that asserts the Spring application context starts without error.

3. THE `shared-mcp-contracts/` module SHALL NOT declare the
   `spring-boot-maven-plugin` repackage goal, because it is a shared library, not
   a runnable JAR; it SHALL declare `<packaging>jar</packaging>` and expose its
   DTO classes on the compile classpath.

4. THE `shared-mcp-contracts/` module SHALL contain Java record or class definitions
   for the MCP_Tool_Contract envelope fields (`requestId`, `businessEntity`,
   `status`, `facts`, `violations`, `permittedActions`, `evidence`,
   `dataClassification`, `expiresAt`) with field-level Javadoc that references
   the contract definition in CLAUDE.md.

5. WHEN the command `mvn verify -pl Middleware` is executed from Repository_Root,
   THE Maven build SHALL compile all seven modules and pass all context-load tests
   without error.

6. IF a Spring_Boot_Service module lacks the context-load test class, THEN THE
   build SHALL fail the test phase so that the gap is caught at integration time.

7. THE `event-sequence-processor/` module SHALL declare a dependency on
   `spring-kafka` in its `pom.xml` because it is the only Phase-0 service that
   consumes Kafka topics; no other Phase-0 service SHALL declare a direct
   `spring-kafka` dependency at scaffold stage unless architectural analysis
   justifies it.

---

### Requirement 4: Portals — Angular Standalone App Scaffolds

**User Story:** As a frontend developer starting work on one of the three portals,
I want each portal to be a valid, independently buildable Angular standalone
application, so that I can develop and serve it without touching the other portals.

#### Acceptance Criteria

1. THE `Portals/` directory SHALL contain three subdirectories: `Admin/`,
   `TraderDesk/`, and `FXTradeBlotter/`.

2. EACH Angular_App SHALL contain at minimum: `package.json`, `angular.json`,
   `tsconfig.json`, `tsconfig.app.json`, `src/main.ts`, `src/app/app.component.ts`,
   `src/app/app.component.html`, `src/index.html`, and a `.editorconfig` that
   inherits from the root `.editorconfig`.

3. THE `package.json` in each Angular_App SHALL declare `@angular/core` and
   `@angular/cli` at the same pinned major version across all three portals.

4. WHEN the command `ng build` is executed within an Angular_App directory, THE
   Angular_App SHALL produce a production-optimised build artifact without error
   using the Angular standalone component model (no `NgModule` declarations in the
   root app component).

5. THE `Admin/` Angular_App README SHALL describe the portal as the operations and
   risk administration interface and list the named placeholder route paths that
   will be implemented in later phases.

6. THE `TraderDesk/` Angular_App README SHALL describe the portal as the
   customer-facing trader portal and list the named placeholder route paths that
   will be implemented in later phases.

7. THE `FXTradeBlotter/` Angular_App README SHALL describe the portal as the
   broker-facing trade blotter and list the named placeholder route paths that will
   be implemented in later phases.

8. IF two Angular_Apps declare different major versions of `@angular/core`, THEN
   THE Repository_Root CI check SHALL fail, preventing version drift across portals.

---

### Requirement 5: Agents — n8n Workflow Layout and File Naming Convention

**User Story:** As an n8n workflow author, I want a defined directory layout and
JSON file naming convention for workflow exports, so that I can place new workflows
in the correct location and reviewers can infer a workflow's role from its file name
alone.

#### Acceptance Criteria

1. THE `Agents/` directory SHALL contain the subdirectory tree:
   `workflows/supervisor/`, `workflows/specialized/`, `workflows/utilities/`,
   and `credentials/`.

2. THE `Agents/workflows/` directory SHALL contain a `README.md` that defines the
   file naming convention: `{category}-{short-description}.workflow.json`, where
   `{category}` is one of `supervisor`, `specialized`, or `utilities`, and
   `{short-description}` uses lowercase kebab-case words.

3. WHEN an n8n_Workflow JSON file is placed in a subdirectory under
   `Agents/workflows/`, THE file name SHALL follow the convention defined in the
   `README.md`; files that violate the convention SHALL be flagged by the
   `.github/workflows/` CI lint check added in a later phase.

4. THE `Agents/workflows/supervisor/` directory SHALL contain a placeholder
   workflow file named `supervisor-trade-operations.workflow.json` that contains a
   minimal valid n8n workflow JSON skeleton (with a `name`, `nodes`, `connections`,
   and `settings` field) so that the import mechanism can be verified.

5. THE `Agents/` directory SHALL contain a top-level `README.md` that states:
   all AI agents in this platform are implemented as n8n workflow JSON exports;
   no Python agent scripts, FastAPI agent servers, or LangChain agent code belongs
   in this directory; and all workflow JSON files must be exported from n8n and not
   hand-authored.

6. IF a file under `Agents/` does not have the `.workflow.json` extension and is
   not a `README.md` or `.gitkeep`, THEN THE Repository_Root CI check SHALL reject
   the file to prevent accidental inclusion of non-workflow artefacts.

---

### Requirement 6: Sidecars — Python Package Structure

**User Story:** As a Python data-science developer building a detection or embedding
sidecar, I want each sidecar to follow a consistent package layout with a
`pyproject.toml`, src-layout, and `Dockerfile`, so that I can build a container
image and run tests using a single, documented command set.

#### Acceptance Criteria

1. THE `Sidecars/` directory SHALL contain four subdirectories:
   `kpi-anomaly-detector/`, `dlq-cluster-analyzer/`,
   `capacity-forecast-model/`, and `log-normalizer/`.

2. EACH Sidecar SHALL contain:
   - `pyproject.toml` using the `[project]` table (PEP 621) with `name`,
     `version`, `requires-python = ">=3.11"`, and a `[build-system]` table
     declaring `hatchling` as the build backend;
   - `src/{package_name}/` directory with `__init__.py` exposing the package
     version string;
   - `tests/` directory with at least one placeholder test file that imports the
     package and asserts the version string is a non-empty string;
   - `Dockerfile` based on `python:3.11-slim` that installs the package and sets
     the default `CMD` to execute the sidecar's entry-point module;
   - `README.md` describing the sidecar's detection or embedding function,
     its inputs and outputs, and the command to build and run the container locally.

3. THE `Sidecars/` directory SHALL contain a `README.md` that states: Python code
   in this directory is ONLY for statistical detection and ML embedding sidecars;
   no business logic, no trade processing, no agent orchestration, and no Spring
   Boot replacement code belongs here; and the entry point for each sidecar must
   emit a compact anomaly envelope compatible with the MCP_Tool_Contract schema
   when a threshold is exceeded.

4. WHEN the command `python -m pytest tests/` is executed within a Sidecar
   directory, THE test runner SHALL execute all placeholder tests and report
   passing with zero failures.

5. WHEN the command `docker build .` is executed within a Sidecar directory,
   THE Docker daemon SHALL build the image without error, confirming that the
   `Dockerfile` and package metadata are self-consistent.

6. IF a Sidecar's `pyproject.toml` does not declare `requires-python = ">=3.11"`,
   THEN THE Repository_Root CI check SHALL fail that sidecar's lint step.

7. THE `kpi-anomaly-detector/` Sidecar README SHALL state that its output envelope
   uses Synthetic_Identifiers; it SHALL include a representative example output
   JSON block using a trade ID with the `FX-` prefix.

---

### Requirement 7: DevOps/Local — Per-Service Docker Compose Files

**User Story:** As a developer running the platform locally, I want a dedicated
`docker-compose.yaml` for each infrastructure service, so that I can start only
the services I need without modifying a single monolithic compose file.

#### Acceptance Criteria

1. THE `DevOps/Local/` directory SHALL contain one `docker-compose.yaml` file for
   each of the following nine infrastructure services, each placed in a named
   subdirectory: `Postgres/`, `Kafka/`, `Grafana/`, `Prometheus/`, `ELK/`,
   `Redis/`, `MongoDB/`, `Neo4j/`, `n8n/`.

2. EACH `docker-compose.yaml` SHALL declare: the service's official Docker Hub or
   vendor image pinned to an explicit version tag (not `latest`); a named Docker
   volume for persistent data; a named Docker network scoped to the
   `DevOps/Local/` stack; and a `healthcheck` configuration appropriate for that
   service.

3. THE `ELK/docker-compose.yaml` SHALL declare three services: `elasticsearch`,
   `logstash`, and `kibana`, with inter-service `depends_on` health conditions so
   that Kibana starts only after Elasticsearch is healthy and Logstash starts only
   after Elasticsearch is healthy.

4. THE `Kafka/docker-compose.yaml` SHALL declare both a broker service and a
   Zookeeper-mode or KRaft-mode controller service as required by the chosen Kafka
   image version.

5. THE `n8n/docker-compose.yaml` SHALL declare the n8n service with a volume-
   mounted data directory and expose port 5678 as the default n8n UI port.

6. EACH `docker-compose.yaml` SHALL define the container network using the bridge
   driver and assign the network a name that follows the pattern
   `fxops-{service-name}-net` (e.g., `fxops-postgres-net`).

7. IF a `docker-compose.yaml` pins an image to the `latest` tag, THEN THE
   Repository_Root CI check SHALL reject the file to enforce reproducible builds.

8. THE `DevOps/Local/` directory SHALL contain a `README.md` that lists all nine
   services, their default ports, the Docker network naming convention, and how to
   use the orchestration scripts defined in Requirement 8.

---

### Requirement 8: DevOps/Local — Orchestration Scripts

**User Story:** As a developer who wants to start or stop the entire local
infrastructure stack with a single command, I want orchestration shell scripts
that wrap the individual Docker Compose files, so that I do not need to remember
nine separate compose commands.

#### Acceptance Criteria

1. THE `DevOps/Local/` directory SHALL contain three Developer_Scripts:
   `docker-all-up.sh`, `docker-all-down.sh`, and `all-status.sh`.

2. THE `docker-all-up.sh` script SHALL, when executed without arguments, run
   `docker compose up -d` for each of the nine service subdirectories in
   `DevOps/Local/` in dependency order: Postgres first, then Kafka, then Redis,
   then MongoDB, then Neo4j, then Elasticsearch (via ELK), then Prometheus, then
   Grafana, then n8n last.

3. WHEN `docker-all-up.sh` is executed with one or more service name arguments
   (e.g., `./docker-all-up.sh Postgres Redis`), THE script SHALL start only the
   named services in the order they appear in the full dependency sequence defined
   in criterion 2.

4. THE `docker-all-down.sh` script SHALL, when executed without arguments, run
   `docker compose down` for each service in the reverse of the start order
   defined in criterion 2, so that dependent services are stopped before their
   dependencies.

5. WHEN `docker-all-down.sh` is executed with one or more service name arguments,
   THE script SHALL stop only the named services.

6. THE `all-status.sh` script SHALL execute `docker compose ps` for each of the
   nine service directories and print a consolidated summary showing each service
   name, its container status, and its exposed ports.

7. EACH Developer_Script SHALL contain a header comment block that states: the
   script's purpose, accepted arguments, and example usage with Synthetic_Identifier
   service names.

8. EACH Developer_Script SHALL be stored with Unix line endings (`LF`) and have
   the executable bit set (`chmod +x`) in the Git repository.

9. IF a Developer_Script references a service subdirectory name that does not exist
   under `DevOps/Local/`, THEN THE script SHALL print an error message to stderr
   and exit with a non-zero exit code.

---

### Requirement 9: Root package.json Developer Commands

**User Story:** As a developer who prefers a single entry point for common local
operations, I want the root `package.json` to expose `start`, `stop`, `status`,
and `install` commands that delegate to the DevOps scripts, so that I can manage
the local environment without navigating into subdirectories.

#### Acceptance Criteria

1. THE Repository_Root SHALL contain a `package.json` that declares the following
   npm scripts: `start`, `stop`, `status`, and `install`.

2. THE `start` script SHALL invoke `DevOps/Local/docker-all-up.sh` with no
   additional arguments so that all services start by default.

3. THE `stop` script SHALL invoke `DevOps/Local/docker-all-down.sh` with no
   additional arguments so that all services stop by default.

4. THE `status` script SHALL invoke `DevOps/Local/all-status.sh`.

5. THE `install` script SHALL execute `npm install` for each Angular_App under
   `Portals/` in sequence (`Admin`, `TraderDesk`, `FXTradeBlotter`) so that all
   portal dependencies are installed with a single command.

6. THE Root_Package_JSON SHALL NOT declare any application dependencies (`dependencies`
   or `devDependencies`) that are not required for the developer-tooling scripts
   themselves; portal-specific dependencies belong in each Angular_App's own
   `package.json`.

7. THE Root_Package_JSON SHALL include a `"private": true` field to prevent
   accidental publication to the npm registry.

8. WHEN `npm run start` is executed from Repository_Root, THE npm runner SHALL
   delegate to `docker-all-up.sh` and exit with the same exit code returned by
   that script.

---

### Requirement 10: .github/workflows Placeholder

**User Story:** As a contributor expecting CI pipelines to be added in a future
phase, I want a `README.md` in the `.github/workflows/` directory that explains
what pipelines will be added, so that the placeholder communicates intent without
implying that CI is already active.

#### Acceptance Criteria

1. THE `.github/` directory SHALL contain a `workflows/` subdirectory containing
   a `README.md`.

2. THE `.github/workflows/README.md` SHALL state: no CI workflow YAML files are
   present in this directory yet; the following pipelines are planned for a future
   phase: Java multi-module build, Angular lint and build, Python sidecar test and
   Docker build, n8n workflow lint, and Docker Compose image-tag validation.

3. THE `.github/workflows/README.md` SHALL note that all CI pipelines will be
   manually triggered GitHub Actions, not automatic push triggers, consistent with
   the deployment model described in CLAUDE.md.

4. IF a YAML file is added to `.github/workflows/` during the initial-setup phase,
   THEN it MUST be accompanied by a corresponding update to the `README.md`
   documenting its purpose; CI workflow files MUST NOT be added silently.

5. THE `.github/` directory SHALL contain a `CODEOWNERS` file that maps the
   following directory prefixes to placeholder team handles:
   `Middleware/` → `@fxops/platform-java`,
   `Portals/` → `@fxops/platform-frontend`,
   `Agents/` → `@fxops/platform-agents`,
   `Sidecars/` → `@fxops/platform-python`,
   `DevOps/` → `@fxops/platform-devops`,
   `docs/` → `@fxops/platform-architecture`.

---

### Requirement 11: docs/ Directory Structure

**User Story:** As an architect maintaining decision records and diagrams, I want
a `docs/` directory with pre-created `adr/` and `diagrams/` subdirectories, so
that contributors know exactly where to place architecture documentation without
inventing ad-hoc locations.

#### Acceptance Criteria

1. THE `docs/` directory SHALL contain two subdirectories: `adr/` and `diagrams/`.

2. THE `docs/adr/` directory SHALL contain a `README.md` that describes the ADR
   format to use (Nygard or MADR), explains the file naming convention
   (`NNNN-{short-title}.md` where `NNNN` is a zero-padded four-digit sequence
   number), and states that each ADR must document context, decision, and
   consequences.

3. THE `docs/adr/` directory SHALL contain the first ADR file,
   `0001-monorepo-language-boundaries.md`, that records the architectural decision
   to restrict microservices to Spring Boot (Java/Maven), agents to n8n workflows,
   and Python to detection/embedding sidecars only, together with the rationale
   drawn from CLAUDE.md.

4. THE `docs/diagrams/` directory SHALL contain a `README.md` that states the
   preferred diagramming formats (Mermaid for text-as-code diagrams embedded in
   Markdown, PlantUML or draw.io for complex architecture diagrams) and the
   convention that no binary diagram files (`.pptx`, `.vsdx`) are committed to the
   repository.

5. THE `docs/` directory SHALL contain a top-level `README.md` that provides a
   table of contents linking to the `adr/` and `diagrams/` subdirectories and to
   the key design documents (`CLAUDE.md`, `PRD.md`, `runtime_agents_catalog.md`)
   at Repository_Root.

---

### Requirement 12: Root Tooling Files

**User Story:** As a contributor setting up a local development environment, I want
root-level tooling files (`.gitignore`, `.editorconfig`, `CONTRIBUTING.md`,
`CODEOWNERS`) to be present and correctly configured, so that my editor, Git, and
CI all apply consistent settings from the first commit.

#### Acceptance Criteria

1. THE Repository_Root SHALL contain a `.gitignore` file that excludes at minimum:
   Java build artefacts (`target/`, `*.class`, `*.jar`);
   IDE metadata (`.idea/`, `*.iml`, `.vscode/settings.json`, `.classpath`,
   `.project`, `.settings/`);
   Angular build output (`dist/`, `.angular/cache/`);
   npm artefacts (`node_modules/`);
   Python artefacts (`__pycache__/`, `*.pyc`, `.venv/`, `dist/`, `build/`,
   `*.egg-info/`);
   Docker-compose overrides (`docker-compose.override.yaml`);
   environment secrets (`.env`, `*.env`, `*.pem`, `*.key`, `secrets/`).

2. THE `.gitignore` file SHALL NOT exclude `docker-compose.yaml` files, because
   those files are versioned infrastructure definitions and must remain tracked.

3. THE Repository_Root SHALL contain an `.editorconfig` file that sets: `root = true`;
   `indent_style = space`; `indent_size = 4` for Java and XML files;
   `indent_size = 2` for TypeScript, HTML, JSON, and YAML files;
   `end_of_line = lf`; `charset = utf-8`; `trim_trailing_whitespace = true`;
   `insert_final_newline = true`.

4. THE Repository_Root SHALL contain a `CONTRIBUTING.md` file that describes:
   the branch naming convention (`feature/`, `fix/`, `docs/` prefixes);
   the pull-request checklist (all seven service scaffolds must compile, all three
   portals must `ng build`, all four sidecar `pytest` suites must pass);
   the synthetic-data policy (no real identifiers — see Requirement 13);
   and how to run the full local stack using `npm run start`.

5. THE Repository_Root `CODEOWNERS` file reference in Requirement 10, criterion 5
   SHALL be located at `.github/CODEOWNERS` (not at `Repository_Root/CODEOWNERS`),
   following GitHub's supported file locations.

6. IF the `.gitignore` file does not exclude `.env` files, THEN THE Repository_Root
   CI check SHALL fail to prevent accidental secret commits.

---

### Requirement 13: Synthetic Data and Public Safeguard Enforcement

**User Story:** As the repository maintainer publishing this as an open-source
reference implementation, I want every scaffold, README, comment, and configuration
example to use only synthetic identifiers and fictional organizations, so that no
real financial institution, person, or confidential operational data is ever
committed to the repository.

#### Acceptance Criteria

1. THE Repository_Root SHALL enforce that all example trade identifiers in
   documentation, code comments, configuration files, and test fixtures use the
   `FX-` prefix followed by digits only (e.g., `FX-000001`, `FX-928734`).

2. THE Repository_Root SHALL enforce that no commit introduces any of the following
   categories of real data: real counterparty or account names; production URLs,
   hostnames, or IP addresses; API keys, tokens, passwords, certificates, or
   secrets; proprietary Kafka topic names, database schemas, or rule thresholds;
   or screenshots, logs, or payloads copied from a real financial system.

3. WHEN a code review or automated scan identifies a candidate real identifier, THE
   Repository_Root CI check SHALL flag the file for human review before merge.

4. THE `shared-mcp-contracts/` module test fixtures SHALL use only Synthetic_Identifiers
   (e.g., `requestId = "req-00001"`, `entityId = "FX-000001"`) in all example
   payloads, Javadoc examples, and unit test data.

5. THE `Agents/` directory SHALL NOT contain any n8n credential values, API keys,
   or connection strings; credential configuration is stored outside the repository
   using the mechanism documented in `Agents/credentials/README.md`.

6. THE `Sidecars/` test fixtures and README example outputs SHALL use
   Synthetic_Identifiers with the `FX-` prefix for all trade references.

7. THE `docs/adr/0001-monorepo-language-boundaries.md` SHALL use only fictional
   example service names and Synthetic_Identifiers in any code or configuration
   snippets it contains; it SHALL NOT reference any real employer, client, or
   production system.

8. IF any file committed to the repository contains a string that matches the
   pattern of a real IBAN, SWIFT/BIC code, real IP address range, or cloud account
   ID, THEN THE Repository_Root CI check SHALL block the commit pending manual
   review.
