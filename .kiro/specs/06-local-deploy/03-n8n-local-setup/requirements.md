# Requirements Document — n8n Local Setup

> **Technology-agnostic spec.** References **Technology Roles** from
> `01-initial-setup/01-technology-stack`. Contains no product names or versions.

## Introduction

This feature defines the **local `AGENT_PLATFORM` instance configuration**:
how the `AGENT_PLATFORM` is started, how workflow JSONs are imported, how MCP
client credentials are configured, how webhook endpoints are registered, and
how the end-to-end tool call chain (`AGENT_PLATFORM` → MCP client → Spring AI
MCP server → `SERVICE_FRAMEWORK` service) is verified.

This spec is the integration point between all previous phases. After completing
this spec, a developer can run a complete local agent workflow: a sidecar
detects an anomaly → POSTs to a webhook → an `AGENT_PLATFORM` workflow fires
→ the agent calls an MCP tool → the `SERVICE_FRAMEWORK` service responds →
the agent produces an output.

All identifiers in examples use the synthetic `FX-` prefix. All workflow and
credential names are fictional.

---

## Glossary

- **AgentPlatformInstance**: The locally running `AGENT_PLATFORM` container
  configured for this platform (per `01-initial-setup/01-technology-stack`
  `AGENT_PLATFORM` role).
- **WorkflowJSON**: An exported `AGENT_PLATFORM` workflow file following the
  naming convention defined in `01-initial-setup/02-repo-skeleton`
  Requirement 5.
- **MCPClientCredential**: The `AGENT_PLATFORM` credential record that
  configures how the agent connects to a `SERVICE_FRAMEWORK` `MCPServer`;
  contains the server base URL and (optionally) an auth token.
- **WebhookEndpoint**: The `AGENT_PLATFORM` webhook URL exposed by a workflow's
  trigger node; used by sidecars and external systems to trigger agent runs.
- **AgentToolCall**: The end-to-end interaction where an `AGENT_PLATFORM`
  workflow invokes an MCP tool on a `SERVICE_FRAMEWORK` `MCPServer` and
  receives a `ToolEnvelope` response.
- **SmokeTest**: A minimal end-to-end verification that confirms the full
  local stack is wired correctly: sidecar → webhook → agent → MCP tool →
  service → `ToolEnvelope` response.

---

## Requirements

### Requirement 1: Agent Platform Instance Configuration

**User Story:** As a developer running locally, I want the `AGENT_PLATFORM`
instance configured and started via the `DevOps/Local/` compose stack so that
I do not need to manually set up the agent runtime.

#### Acceptance Criteria

1. THE `DevOps/Local/AGENT_PLATFORM/docker-compose.yaml` SHALL declare the
   `AGENT_PLATFORM` service with: a pinned image version (never `latest`); a
   volume-mounted data directory persisting workflow definitions and credentials
   across container restarts; and the `AGENT_PLATFORM`'s default UI port
   exposed (per `01-initial-setup/02-repo-skeleton` Requirement 7 criterion 5).
2. THE `AgentPlatformInstance` SHALL connect to the same `CONTAINER_RUNTIME`
   compose network as the `SERVICE_FRAMEWORK` services so that it can reach
   `MCPServer` endpoints by service name.
3. THE `AgentPlatformInstance` SHALL have environment variables configured for:
   the `AGENT_PLATFORM` encryption key (a placeholder value for local dev —
   never a real secret in the repository), the database backend (using the
   `RELATIONAL_STORE` compose service for persistence), and the
   `AGENT_PLATFORM` execution mode (queue mode for scalable local testing).
4. ALL `AGENT_PLATFORM` environment variable values in the compose file SHALL
   use placeholder values or references to a `.env` file; real secrets SHALL
   NOT be committed to the repository (per GP-Rq-14).

---

### Requirement 2: Workflow JSON Import

**User Story:** As a developer setting up a clean local environment, I want
all `WorkflowJSON` files imported into the `AGENT_PLATFORM` automatically so
that agents are ready to test without manual import steps.

#### Acceptance Criteria

1. THE `DevOps/Local/AGENT_PLATFORM/` setup SHALL include a script or compose
   initialization step that imports all `WorkflowJSON` files from
   `Agents/workflows/` into the `AgentPlatformInstance` on first startup.
2. THE import script SHALL be idempotent: running it twice SHALL NOT create
   duplicate workflows; it SHALL update existing workflows with the same name.
3. THE import script SHALL log the name and import status of each workflow so
   that a developer can verify which workflows were imported.
4. WHEN a `WorkflowJSON` file fails to import (e.g. invalid JSON schema),
   THE script SHALL log the error with the filename and continue importing
   remaining files rather than aborting.
5. THE import script SHALL use only `AGENT_PLATFORM` public API calls (REST or
   CLI) to import workflows; it SHALL NOT directly modify the `AGENT_PLATFORM`
   database.

---

### Requirement 3: MCP Client Credentials Configuration

**User Story:** As an agent developer, I want `MCPClientCredential` records
configured in the `AGENT_PLATFORM` for every `SERVICE_FRAMEWORK` `MCPServer`
so that agent workflows can call MCP tools without per-workflow credential
setup.

#### Acceptance Criteria

1. THE `DevOps/Local/AGENT_PLATFORM/` setup SHALL include a credential
   provisioning script that creates one `MCPClientCredential` per
   `Service_Module` that exposes a `MCPServer`, using the server names and
   endpoints defined in `DevOps/Local/AGENT_PLATFORM/mcp-servers.json`.
2. EACH `MCPClientCredential` SHALL set the `MCPServer` base URL to the
   `CONTAINER_RUNTIME` service name and MCP port (e.g.
   `http://trade-lifecycle-service:8081`), not `localhost`.
3. THE credential provisioning script SHALL be idempotent: running it twice
   SHALL NOT create duplicate credentials.
4. NO `MCPClientCredential` SHALL contain a real API key, token, or secret
   in the repository; local credentials use placeholder values with a comment
   marking where production credentials will be injected in cloud deploy.
5. THE credential provisioning script SHALL verify connectivity to each
   `MCPServer` endpoint after provisioning and log a WARN for any unreachable
   server.

---

### Requirement 4: Webhook Endpoint Registration

**User Story:** As a sidecar or external system, I want webhook endpoints
registered for each agent workflow's trigger node so that I can POST an
`AnomalyEnvelope` and reliably trigger the correct agent run.

#### Acceptance Criteria

1. EVERY `AGENT_PLATFORM` workflow that is sidecar-triggered SHALL expose a
   webhook trigger node; the resulting `WebhookEndpoint` URL SHALL be
   documented in `DevOps/Local/AGENT_PLATFORM/sidecar-webhooks.md`.
2. THE `sidecar-webhooks.md` document SHALL map each `Sidecar` name to its
   `WebhookEndpoint` URL and the `WorkflowJSON` file it triggers.
3. THE `DevOps/Local/AGENT_PLATFORM/` setup SHALL include a verification step
   that confirms each registered `WebhookEndpoint` returns a `200` or `201`
   on a test POST with an empty body.
4. `WebhookEndpoint` URLs SHALL use the `CONTAINER_RUNTIME` service name
   (not `localhost`) as the hostname when called from other compose services,
   and `localhost` when called from outside the compose network (e.g. a
   developer's terminal).
5. ALL example `AnomalyEnvelope` payloads in `sidecar-webhooks.md` SHALL use
   `SyntheticData` (`FX-` prefixed `tradeId`s, fictional detector outputs).

---

### Requirement 5: End-to-End Smoke Test

**User Story:** As a developer completing the local deploy phase, I want a
documented smoke test that verifies the full tool call chain works end-to-end
so that I know the wiring is correct before writing agent logic.

#### Acceptance Criteria

1. THE `DevOps/Local/` documentation SHALL include a `SMOKE-TEST.md` that
   describes a step-by-step end-to-end smoke test for the local stack.
2. THE smoke test SHALL verify: (a) POST a synthetic `AnomalyEnvelope` to a
   `WebhookEndpoint`; (b) confirm the `AGENT_PLATFORM` workflow run appears in
   the execution log; (c) confirm the workflow called at least one MCP tool;
   (d) confirm the tool returned a `ToolEnvelope` with `status = SUCCESS`.
3. THE smoke test SHALL use a `SyntheticData` `tradeId` (e.g. `FX-000001`)
   for all test payloads; no real trade data SHALL be used.
4. THE smoke test SHALL be executable by running a single shell script
   (`DevOps/Local/smoke-test.sh`) that performs all steps and exits with
   code `0` on success and non-zero on failure.
5. THE smoke test script SHALL print a clear pass/fail summary for each step
   so that a developer can identify exactly which part of the wiring failed.
