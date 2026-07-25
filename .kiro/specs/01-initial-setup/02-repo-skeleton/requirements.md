# Requirements Document — Repository Skeleton (Initial Setup)

> **Technology-agnostic spec.** References **Technology Roles** from
> `01-initial-setup/01-technology-stack`. Contains no product names or versions.

## Introduction

This feature establishes the complete initial repository structure for the
**Forex-Trade-Operations-Intelligence** monorepo. The monorepo is a spec-driven,
publicly available reference implementation of a runtime-intelligence platform
for foreign-exchange trade operations. The initial setup scaffolds every top-level
directory, service skeleton, portal app, agent workflow layout, detection/embedding
sidecar packages, local infrastructure compose files, orchestration scripts, and
root developer-tooling files so that all subsequent build phases start from a
coherent, reproducible baseline.

No business logic is implemented at this stage. Every scaffold is an empty-but-valid
project skeleton that compiles, passes a lint check, or starts a container, as
appropriate to its technology role. All example data and identifiers used in
comments, READMEs, and configuration must follow the synthetic-data policy defined
in Requirement 13.

---

## Glossary

- **Monorepo**: The single Git repository `Forex-Trade-Operations-Intelligence/`
  that contains all components of the platform.
- **Repository_Root**: The top-level directory of the Monorepo.
- **Middleware**: The `Middleware/` subdirectory; contains all `SERVICE_LANGUAGE`
  microservices built with `SERVICE_FRAMEWORK` and `SERVICE_BUILD_TOOL`.
- **Portals**: The `Portals/` subdirectory; contains the three standalone
  `FRONTEND_FRAMEWORK` applications.
- **Agents**: The `Agents/` subdirectory; contains `AGENT_PLATFORM` workflow
  JSON exports and credential documentation only.
- **Sidecars**: The `Sidecars/` subdirectory; contains `SIDECAR_LANGUAGE`
  detection and embedding packages built with `SIDECAR_BUILD_BACKEND`.
- **DevOps**: The `DevOps/` subdirectory; contains all infrastructure-as-code
  for local and future cloud environments.
- **DevOps_Local**: The `DevOps/Local/` subdirectory; contains per-service
  `CONTAINER_RUNTIME` compose files and orchestration shell scripts.
- **Service_Module**: Any build module under `Middleware/` that produces a
  runnable microservice using `SERVICE_FRAMEWORK`.
- **Portal_App**: Any standalone frontend application under `Portals/` that
  has its own dependency manifest and build configuration, built with
  `FRONTEND_FRAMEWORK`.
- **Agent_Workflow**: A JSON-formatted workflow export from the `AGENT_PLATFORM`
  placed under `Agents/workflows/`.
- **Sidecar**: Any `SIDECAR_LANGUAGE` package under `Sidecars/` that provides
  statistical detection or embedding functionality.
- **Parent_Build_Descriptor**: The root build descriptor at `Middleware/` that
  declares all child modules and shared dependency management for the
  `SERVICE_BUILD_TOOL`.
- **Scaffold**: An empty-but-valid skeleton that satisfies the toolchain's
  minimum structural requirements (e.g., a `Service_Module` with a main
  application entry point and build descriptor referencing the
  `Parent_Build_Descriptor`).
- **Synthetic_Identifier**: A placeholder trade, counterparty, rule, or entity
  identifier that does not correspond to any real financial institution, person,
  or system. All trade identifiers MUST use the `FX-` prefix (e.g., `FX-000001`).
- **Developer_Script**: An executable shell script under `DevOps/Local/` that
  wraps `CONTAINER_RUNTIME` compose commands for local developer use.
- **Root_Package_JSON**: The `package.json` at `Repository_Root/package.json`
  that exposes developer convenience commands (`start`, `stop`, `status`,
  `install`).
- **CODEOWNERS**: The `.github/CODEOWNERS` file that maps directory prefixes to
  responsible teams.
- **ADR**: Architecture Decision Record; a Markdown file stored under `docs/adr/`.
- **MCP_Tool_Contract**: The agent envelope schema (`requestId`, `businessEntity`,
  `status`, `facts`, `violations`, `permittedActions`, `evidence`,
  `dataClassification`, `expiresAt`) defined in the shared DTO library, exposed
  via the `AGENT_TOOL_PROTOCOL`.

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
   the architectural constraints document, and states that all examples use
   `Synthetic_Identifier`s.

3. WHEN a top-level directory is created, THE Repository_Root SHALL ensure that
   directory contains a `README.md` explaining its purpose, the technology role
   it contains, and the conventions contributors must follow within it.

4. THE `Agents/credentials/README.md` file SHALL explain that no credential
   values are stored in the repository, describe the expected secret-manager or
   environment-variable mechanism, and provide an example using
   `Synthetic_Identifier` values only.

5. IF a directory exists but lacks a `README.md`, THEN THE Repository_Root SHALL
   treat that directory as incomplete and the scaffold as invalid.

---

### Requirement 2: Middleware — Multi-Module Build Structure

**User Story:** As a developer building a new microservice, I want a
`Parent_Build_Descriptor` and a consistent per-module directory convention so
that I can add a new service by following a repeatable template without manually
assembling build boilerplate.

#### Acceptance Criteria

1. THE `Middleware/` directory SHALL contain a `Parent_Build_Descriptor` that
   declares packaging type `pom` and lists each `Service_Module` as a child
   module entry.

2. THE `Parent_Build_Descriptor` SHALL declare a dependency-management section
   that pins the `SERVICE_FRAMEWORK` BOM, `AGENT_TOOL_PROTOCOL` BOM, and common
   testing libraries (per the `UNIT_TEST_FRAMEWORK`, `INTEGRATION_TEST_HARNESS`,
   `WEB_LAYER_TEST`, and `PROPERTY_TEST` roles) to explicit versions; no
   individual module shall re-declare these versions.

3. THE `Parent_Build_Descriptor` SHALL declare shared build plugin configuration
   targeting the pinned `SERVICE_LANGUAGE` version for source and bytecode
   compatibility across all modules.

4. WHEN a new `Service_Module` is added to `Middleware/`, THE
   `Parent_Build_Descriptor` SHALL be updated to include that module before the
   scaffold is considered complete.

5. THE `Middleware/` directory SHALL contain a `README.md` that documents the
   `Parent_Build_Descriptor` structure, the `SERVICE_LANGUAGE` version
   requirement, how to add a new module, and which modules exist at initial setup.

6. IF a `Service_Module` build descriptor does not declare its parent as the
   `Parent_Build_Descriptor`, THEN THE scaffold SHALL be treated as
   non-conforming and must be corrected before merging.

---

### Requirement 3: Phase-0 Service_Module Scaffolds

**User Story:** As a developer beginning implementation of a microservice, I want
each Phase-0 service to be a compilable, runnable skeleton using `SERVICE_FRAMEWORK`,
so that I can wire in business logic incrementally without restructuring the
module layout.

#### Acceptance Criteria

1. THE `Middleware/` directory SHALL contain the following `Service_Module`
   subdirectories at initial setup:
   `shared-domain-contracts/`, `trade-ingest-service/`,
   `trade-lifecycle-service/`, `risk-calculation-service/`,
   `eod-processing-service/`, `business-calendar-service/`,
   `state-reconciliation-service/`.

2. EACH `Service_Module` SHALL contain:
   - A build descriptor referencing the `Parent_Build_Descriptor`;
   - A main application entry point annotated for `SERVICE_FRAMEWORK`
     auto-configuration;
   - A configuration file setting `spring.application.name` to the service name
     in kebab-case;
   - A placeholder test class asserting the application context starts without
     error.

3. THE `shared-domain-contracts/` module SHALL NOT be configured as a runnable
   application; it SHALL be a shared library artifact (compile-scope dependency)
   with no `SERVICE_FRAMEWORK` runtime repackaging, as defined in
   `02-microservices/01-shared-domain-contracts`.

4. THE `shared-domain-contracts/` module SHALL contain the `MCP_Tool_Contract`
   DTO definitions with field-level documentation; these DTOs are the shared
   envelope used by every service when exposing `AGENT_TOOL_PROTOCOL` capabilities
   in a later phase.

5. WHEN the `SERVICE_BUILD_TOOL` verify command is executed against `Middleware/`
   from `Repository_Root`, THE build SHALL compile all modules and pass all
   context-load tests without error.

6. IF a `Service_Module` lacks its context-load test class, THEN THE build SHALL
   fail the test phase so that the gap is caught at build time.

7. THE `state-reconciliation-service/` module SHALL declare a dependency on the
   `EVENT_STREAM` client role in its build descriptor because it is the only
   Phase-0 service that reads the event stream at the reconciliation layer; other
   Phase-0 scaffolds that do not yet consume events SHALL NOT declare that
   dependency until their feature spec justifies it.

---

### Requirement 4: Portals — Frontend App Scaffolds

**User Story:** As a frontend developer starting work on one of the three portals,
I want each portal to be a valid, independently buildable `FRONTEND_FRAMEWORK`
application, so that I can develop and serve it without touching the other portals.

#### Acceptance Criteria

1. THE `Portals/` directory SHALL contain three subdirectories: `Admin/`,
   `TraderDesk/`, and `FXTradeBlotter/`.

2. EACH `Portal_App` SHALL contain at minimum: a dependency manifest, a
   framework build configuration file, TypeScript configuration, an application
   entry point, a root component, a root HTML template, and an editor-config
   inheriting from the root `.editorconfig`.

3. THE dependency manifest in each `Portal_App` SHALL declare the
   `FRONTEND_FRAMEWORK` at the same pinned major version across all three portals
   to prevent version drift.

4. WHEN the `FRONTEND_FRAMEWORK` build command is executed within a `Portal_App`
   directory, THE app SHALL produce a production-optimised build artifact without
   error using the `FRONTEND_FRAMEWORK` standalone component model (no legacy
   module declarations in the root component).

5. THE `Admin/` `Portal_App` README SHALL describe the portal as the operations
   and risk administration interface and list the named placeholder route paths
   to be implemented in a later phase.

6. THE `TraderDesk/` `Portal_App` README SHALL describe the portal as the
   customer-facing trader portal and list the named placeholder route paths to be
   implemented in a later phase.

7. THE `FXTradeBlotter/` `Portal_App` README SHALL describe the portal as the
   broker-facing trade blotter and list the named placeholder route paths to be
   implemented in a later phase.

8. IF two `Portal_App`s declare different major versions of the
   `FRONTEND_FRAMEWORK` core dependency, THEN THE Repository_Root CI check SHALL
   fail, preventing version drift across portals.

---

### Requirement 5: Agents — Workflow Layout and File Naming Convention

**User Story:** As an `AGENT_PLATFORM` workflow author, I want a defined directory
layout and JSON file naming convention, so that I can place new workflows in the
correct location and reviewers can infer a workflow's role from its file name alone.

#### Acceptance Criteria

1. THE `Agents/` directory SHALL contain the subdirectory tree:
   `workflows/supervisor/`, `workflows/specialized/`, `workflows/utilities/`,
   and `credentials/`.

2. THE `Agents/workflows/` directory SHALL contain a `README.md` that defines the
   file naming convention: `{category}-{short-description}.workflow.json`, where
   `{category}` is one of `supervisor`, `specialized`, or `utilities`, and
   `{short-description}` uses lowercase kebab-case words.

3. WHEN an `Agent_Workflow` JSON file is placed under `Agents/workflows/`, THE
   file name SHALL follow the convention in the `README.md`; files that violate
   the convention SHALL be flagged by a CI lint check in a later phase.

4. THE `Agents/workflows/supervisor/` directory SHALL contain a placeholder
   workflow file named `supervisor-trade-operations.workflow.json` containing a
   minimal valid `AGENT_PLATFORM` workflow skeleton (with `name`, `nodes`,
   `connections`, and `settings` fields) so that the import mechanism can be
   verified.

5. THE `Agents/` directory SHALL contain a top-level `README.md` stating: all AI
   agents in this platform are implemented as `AGENT_PLATFORM` workflow JSON
   exports; no `SIDECAR_LANGUAGE` agent scripts, FastAPI agent servers, or
   third-party agent framework code belongs in this directory; and all workflow
   JSON files must be exported from the `AGENT_PLATFORM` and not hand-authored.

6. IF a file under `Agents/` does not have the `.workflow.json` extension and is
   not a `README.md` or `.gitkeep`, THEN THE Repository_Root CI check SHALL reject
   the file to prevent accidental inclusion of non-workflow artefacts.

---

### Requirement 6: Sidecars — Package Structure

**User Story:** As a `SIDECAR_LANGUAGE` developer building a detection or embedding
sidecar, I want each sidecar to follow a consistent package layout with a build
descriptor, src-layout, and `CONTAINER_RUNTIME` image definition, so that I can
build a container image and run tests using a single, documented command.

#### Acceptance Criteria

1. THE `Sidecars/` directory SHALL contain four subdirectories:
   `kpi-anomaly-detector/`, `dlq-cluster-analyzer/`,
   `capacity-forecast-model/`, and `log-normalizer/`.

2. EACH `Sidecar` SHALL contain:
   - A build descriptor using the PEP 621 `[project]` table with `name`,
     `version`, a `requires-python` constraint matching the `SIDECAR_LANGUAGE`
     role, and a `[build-system]` table declaring `SIDECAR_BUILD_BACKEND` as the
     build backend;
   - A `src/{package_name}/` directory with `__init__.py` exposing the package
     version string;
   - A `tests/` directory with at least one placeholder test that imports the
     package and asserts the version string is non-empty;
   - A `CONTAINER_RUNTIME` image definition based on the `SIDECAR_LANGUAGE`
     slim base image that installs the package and sets the default entry point;
   - A `README.md` describing the sidecar's detection or embedding function,
     its inputs and outputs, and the command to build and run the container
     locally.

3. THE `Sidecars/` directory SHALL contain a `README.md` stating: `SIDECAR_LANGUAGE`
   code in this directory is ONLY for statistical detection and embedding sidecars;
   no business logic, no trade processing, no agent orchestration, and no
   `SERVICE_FRAMEWORK` replacement code belongs here; the entry point for each
   sidecar must emit a compact anomaly envelope compatible with the
   `MCP_Tool_Contract` schema when a detection threshold is exceeded.

4. WHEN the `SIDECAR_LANGUAGE` test command is executed within a `Sidecar`
   directory, THE test runner SHALL execute all placeholder tests and report
   passing with zero failures.

5. WHEN the `CONTAINER_RUNTIME` build command is executed within a `Sidecar`
   directory, THE runtime SHALL build the image without error.

6. IF a `Sidecar`'s build descriptor does not declare a `requires-python`
   constraint matching the `SIDECAR_LANGUAGE` role version, THEN THE
   Repository_Root CI check SHALL fail that sidecar's lint step.

7. THE `kpi-anomaly-detector/` `Sidecar` README SHALL include a representative
   example output JSON block using `Synthetic_Identifier` values (e.g., a trade
   ID with the `FX-` prefix).

---

### Requirement 7: DevOps/Local — Per-Service Compose Files

**User Story:** As a developer running the platform locally, I want a dedicated
`CONTAINER_RUNTIME` compose file for each infrastructure service so that I can
start only the services I need without modifying a single monolithic compose file.

#### Acceptance Criteria

1. THE `DevOps/Local/` directory SHALL contain one compose file for each of the
   following nine infrastructure service roles, each placed in a named
   subdirectory: `RELATIONAL_STORE/`, `EVENT_STREAM/`, `OBSERVABILITY_METRICS/`,
   `OBSERVABILITY_LOGGING/`, `CACHE/`, `DOCUMENT_STORE/`, `GRAPH_STORE/`,
   `AGENT_PLATFORM/`, and a combined metrics-visualization subdirectory for
   `OBSERVABILITY_METRICS` dashboards.

2. EACH compose file SHALL declare: the service's official image pinned to an
   explicit version tag (never `latest`); a named volume for persistent data; a
   named network scoped to the `DevOps/Local/` stack; and a `healthcheck`
   configuration appropriate for that service role.

3. THE `OBSERVABILITY_LOGGING` compose file SHALL declare three services
   (the search store, the log pipeline, and the visualization layer) with
   inter-service `depends_on` health conditions so that the visualization layer
   starts only after the search store is healthy, and the log pipeline starts only
   after the search store is healthy.

4. THE `EVENT_STREAM` compose file SHALL declare a broker service and any
   required controller or coordination service as required by the `EVENT_STREAM`
   role's KRaft or equivalent mode configuration.

5. THE `AGENT_PLATFORM` compose file SHALL declare the `AGENT_PLATFORM` service
   with a volume-mounted data directory and expose the `AGENT_PLATFORM`'s default
   UI port.

6. EACH compose file SHALL define the container network using the bridge driver
   and assign the network a name that follows the pattern
   `fxops-{role-short-name}-net` (e.g., `fxops-relational-net`).

7. IF a compose file pins an image to the `latest` tag, THEN THE Repository_Root
   CI check SHALL reject the file to enforce reproducible builds.

8. THE `DevOps/Local/` directory SHALL contain a `README.md` listing all nine
   service roles, their default ports, the Docker network naming convention, and
   how to use the orchestration scripts defined in Requirement 8.

---

### Requirement 8: DevOps/Local — Orchestration Scripts

**User Story:** As a developer who wants to start or stop the entire local
infrastructure stack with a single command, I want orchestration shell scripts
that wrap the individual compose files so that I do not need to remember nine
separate compose commands.

#### Acceptance Criteria

1. THE `DevOps/Local/` directory SHALL contain three `Developer_Script`s:
   `docker-all-up.sh`, `docker-all-down.sh`, and `all-status.sh`.

2. THE `docker-all-up.sh` script SHALL, when executed without arguments, run the
   compose up command for each of the nine service subdirectories in dependency
   order: `RELATIONAL_STORE` first, then `EVENT_STREAM`, then `CACHE`, then
   `DOCUMENT_STORE`, then `GRAPH_STORE`, then `OBSERVABILITY_LOGGING`, then
   `OBSERVABILITY_METRICS`, then metrics-visualization, then `AGENT_PLATFORM` last.

3. WHEN `docker-all-up.sh` is executed with one or more service-role name
   arguments, THE script SHALL start only the named services in the order they
   appear in the full dependency sequence defined in criterion 2.

4. THE `docker-all-down.sh` script SHALL, when executed without arguments, run
   the compose down command for each service in the reverse of the start order,
   so that dependent services are stopped before their dependencies.

5. WHEN `docker-all-down.sh` is executed with one or more service-role name
   arguments, THE script SHALL stop only the named services.

6. THE `all-status.sh` script SHALL execute the compose status command for each
   of the nine service directories and print a consolidated summary showing each
   service role, its container status, and its exposed ports.

7. EACH `Developer_Script` SHALL contain a header comment block stating: the
   script's purpose, accepted arguments, and example usage with
   `Synthetic_Identifier` service names.

8. EACH `Developer_Script` SHALL be stored with Unix line endings (`LF`) and have
   the executable bit set in the Git repository.

9. IF a `Developer_Script` references a service subdirectory name that does not
   exist under `DevOps/Local/`, THEN THE script SHALL print an error message to
   stderr and exit with a non-zero exit code.

---

### Requirement 9: Root Package Developer Commands

**User Story:** As a developer who prefers a single entry point for common local
operations, I want the `Root_Package_JSON` to expose `start`, `stop`, `status`,
and `install` commands that delegate to the DevOps scripts, so that I can manage
the local environment without navigating into subdirectories.

#### Acceptance Criteria

1. THE Repository_Root SHALL contain a `Root_Package_JSON` that declares the
   following scripts: `start`, `stop`, `status`, and `install`.

2. THE `start` script SHALL invoke `DevOps/Local/docker-all-up.sh` with no
   additional arguments so that all services start by default.

3. THE `stop` script SHALL invoke `DevOps/Local/docker-all-down.sh` with no
   additional arguments so that all services stop by default.

4. THE `status` script SHALL invoke `DevOps/Local/all-status.sh`.

5. THE `install` script SHALL execute the `FRONTEND_FRAMEWORK` dependency
   install command for each `Portal_App` under `Portals/` in sequence
   (`Admin`, `TraderDesk`, `FXTradeBlotter`) so that all portal dependencies
   are installed with a single command.

6. THE `Root_Package_JSON` SHALL NOT declare application dependencies that are
   not required for the developer-tooling scripts themselves; portal-specific
   dependencies belong in each `Portal_App`'s own dependency manifest.

7. THE `Root_Package_JSON` SHALL include a `"private": true` field to prevent
   accidental publication to the package registry.

8. WHEN the root start command is executed from `Repository_Root`, THE runner
   SHALL delegate to `docker-all-up.sh` and exit with the same exit code returned
   by that script.

---

### Requirement 10: .github/workflows Placeholder

**User Story:** As a contributor expecting CI pipelines to be added in a future
phase, I want a `README.md` in the `.github/workflows/` directory explaining what
pipelines will be added, so that the placeholder communicates intent without
implying CI is already active.

#### Acceptance Criteria

1. THE `.github/` directory SHALL contain a `workflows/` subdirectory containing
   a `README.md`.

2. THE `.github/workflows/README.md` SHALL state: no CI workflow files are present
   yet; the following pipelines are planned: `SERVICE_BUILD_TOOL` multi-module
   build, `FRONTEND_FRAMEWORK` lint and build, `SIDECAR_LANGUAGE` test and
   `CONTAINER_RUNTIME` build, `AGENT_PLATFORM` workflow lint, and compose
   image-tag validation.

3. THE `.github/workflows/README.md` SHALL note that all CI pipelines will be
   manually triggered and not automatic push triggers.

4. IF a CI workflow file is added during the initial-setup phase, THEN it MUST be
   accompanied by a corresponding update to the `README.md` documenting its
   purpose; workflow files MUST NOT be added silently.

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
a `docs/` directory with pre-created `adr/` and `diagrams/` subdirectories so
that contributors know exactly where to place architecture documentation.

#### Acceptance Criteria

1. THE `docs/` directory SHALL contain two subdirectories: `adr/` and `diagrams/`.

2. THE `docs/adr/` directory SHALL contain a `README.md` describing the ADR
   format (Nygard or MADR), the file naming convention
   (`NNNN-{short-title}.md` with zero-padded four-digit sequence number), and
   the requirement that each ADR documents context, decision, and consequences.

3. THE `docs/adr/` directory SHALL contain the first ADR file
   `0001-monorepo-language-boundaries.md` recording the architectural decision to
   restrict microservices to `SERVICE_LANGUAGE`/`SERVICE_FRAMEWORK`, agents to
   `AGENT_PLATFORM` workflows, and `SIDECAR_LANGUAGE` to detection/embedding
   sidecars only, together with the rationale.

4. THE `docs/diagrams/` directory SHALL contain a `README.md` stating the
   preferred diagramming formats (Mermaid for text-as-code diagrams, structured
   diagram tools for complex architecture diagrams) and the convention that no
   binary diagram files are committed.

5. THE `docs/` directory SHALL contain a top-level `README.md` providing a table
   of contents linking to `adr/` and `diagrams/` subdirectories and to the key
   design documents at `Repository_Root`.

---

### Requirement 12: Root Tooling Files

**User Story:** As a contributor setting up a local development environment, I
want root-level tooling files (`.gitignore`, `.editorconfig`, `CONTRIBUTING.md`,
`CODEOWNERS`) to be present and correctly configured so that my editor, Git, and
CI all apply consistent settings from the first commit.

#### Acceptance Criteria

1. THE Repository_Root SHALL contain a `.gitignore` file that excludes at minimum:
   `SERVICE_BUILD_TOOL` build artefacts; IDE metadata; `FRONTEND_FRAMEWORK` build
   output and cache; package manager artefacts; `SIDECAR_LANGUAGE` artefacts and
   virtual environments; `CONTAINER_RUNTIME` compose overrides; and environment
   secrets (`.env`, `*.env`, `*.pem`, `*.key`, `secrets/`).

2. THE `.gitignore` file SHALL NOT exclude `CONTAINER_RUNTIME` compose files,
   because those files are versioned infrastructure definitions and must remain
   tracked.

3. THE Repository_Root SHALL contain an `.editorconfig` file that sets:
   `root = true`; `indent_style = space`; `indent_size = 4` for
   `SERVICE_LANGUAGE` and XML files; `indent_size = 2` for TypeScript, HTML,
   JSON, and YAML files; `end_of_line = lf`; `charset = utf-8`;
   `trim_trailing_whitespace = true`; `insert_final_newline = true`.

4. THE Repository_Root SHALL contain a `CONTRIBUTING.md` file describing: the
   branch naming convention (`feature/`, `fix/`, `docs/` prefixes); the
   pull-request checklist (all `Service_Module` scaffolds must compile, all
   `Portal_App`s must build, all `Sidecar` test suites must pass); the
   synthetic-data policy; and how to run the full local stack.

5. THE `.github/CODEOWNERS` file (per Requirement 10, criterion 5) SHALL be
   located at `.github/CODEOWNERS`, following the supported file location
   convention.

6. IF the `.gitignore` file does not exclude `.env` files, THEN THE
   Repository_Root CI check SHALL fail to prevent accidental secret commits.

---

### Requirement 13: Synthetic Data and Public Safeguard Enforcement

**User Story:** As the repository maintainer publishing this as an open-source
reference implementation, I want every scaffold, README, comment, and
configuration example to use only synthetic identifiers and fictional
organizations so that no real financial institution, person, or confidential
operational data is ever committed.

#### Acceptance Criteria

1. THE Repository_Root SHALL enforce that all example trade identifiers in
   documentation, code comments, configuration files, and test fixtures use the
   `FX-` prefix followed by digits only (e.g., `FX-000001`, `FX-928734`).

2. THE Repository_Root SHALL enforce that no commit introduces: real counterparty
   or account names; production URLs, hostnames, or IP addresses; API keys,
   tokens, passwords, certificates, or secrets; proprietary topic names, database
   schemas, or rule thresholds; or screenshots, logs, or payloads copied from a
   real financial system.

3. WHEN a code review or automated scan identifies a candidate real identifier,
   THE Repository_Root CI check SHALL flag the file for human review before merge.

4. THE `shared-domain-contracts/` module test fixtures SHALL use only
   `Synthetic_Identifier`s (e.g., `requestId = "req-00001"`,
   `entityId = "FX-000001"`) in all example payloads, documentation examples,
   and unit test data.

5. THE `Agents/` directory SHALL NOT contain any `AGENT_PLATFORM` credential
   values, API keys, or connection strings; credential configuration is stored
   outside the repository using the mechanism documented in
   `Agents/credentials/README.md`.

6. THE `Sidecars/` test fixtures and README example outputs SHALL use
   `Synthetic_Identifier`s with the `FX-` prefix for all trade references.

7. THE `docs/adr/0001-monorepo-language-boundaries.md` SHALL use only fictional
   example service names and `Synthetic_Identifier`s in any code or configuration
   snippets; it SHALL NOT reference any real employer, client, or production system.

8. IF any file committed to the repository contains a string matching the pattern
   of a real IBAN, SWIFT/BIC code, real IP address range, or cloud account ID,
   THEN THE Repository_Root CI check SHALL block the commit pending manual review.
