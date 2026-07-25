# Requirements Document — MCP Server Setup

> **Technology-agnostic spec.** References **Technology Roles** from
> `01-initial-setup/01-technology-stack`. Contains no product names or versions.

## Introduction

This feature wires the **`AGENT_TOOL_PROTOCOL` (MCP) server layer** into the
`SERVICE_FRAMEWORK` microservices so that `AGENT_PLATFORM` agents can discover
and call typed service tools. It covers the `shared-mcp-contracts` library
(the tool envelope schema), the per-service MCP server configuration, tool
registration conventions, and a local MCP gateway configuration that the
`AGENT_PLATFORM` uses to discover all available tools.

This is the **first time Spring AI and MCP dependencies appear** in the
`Middleware/` codebase — they are explicitly excluded from phases 01–05 per
the MASTER-PLAN architectural decision. No MCP dependency belongs in any
service spec before this phase.

All identifiers in examples use the synthetic `FX-` prefix. All tool names
and service names are fictional.

---

## Glossary

- **MCPServer**: The `AGENT_TOOL_PROTOCOL` server embedded in each
  `Service_Module`, exposing typed tools that the `AGENT_PLATFORM` can call.
- **MCPTool**: A named, typed capability exposed by a `MCPServer`; has an
  `inputSchema` (what the agent sends) and a structured output (what the
  service returns).
- **ToolEnvelope**: The standard `MCP_Tool_Contract` response wrapper:
  `{requestId, businessEntity, status, facts, violations, permittedActions,
  evidence, dataClassification, expiresAt}`.
- **SharedMCPContracts**: The `shared-mcp-contracts` `Service_Module` — the
  shared library that defines `ToolEnvelope` and all common MCP DTOs used
  across services.
- **MCPGateway**: The local configuration (registered in the `AGENT_PLATFORM`)
  that lists all `MCPServer` endpoints so the agent discovers all tools in
  one place.
- **ToolRisk**: The risk classification of a tool call: **L** (read/explain
  only), **M** (blocks/quarantines/holds a process), **H** (moves money,
  state, or config — always human-gated).
- **GatedTool**: An `MCPTool` with `ToolRisk` M or H that requires a
  `ReplayApproval` or equivalent HITL authorization before execution.
- **ReadTool**: An `MCPTool` with `ToolRisk` L — safe to call without approval.

---

## Requirements

### Requirement 1: Shared MCP Contracts Library

**User Story:** As a service developer exposing MCP tools, I want a single
shared library that defines the `ToolEnvelope` and common DTOs so that every
service returns identically shaped responses that the `AGENT_PLATFORM` can
parse uniformly.

#### Acceptance Criteria

1. THE `shared-mcp-contracts` `Service_Module` SHALL define the `ToolEnvelope`
   record/class with fields: `requestId` (UUID), `businessEntity` (string),
   `status` (`SUCCESS` | `PARTIAL` | `FAILURE`), `facts` (list of key-value
   pairs), `violations` (list of violation strings), `permittedActions` (list
   of action-name strings from a fixed catalogue), `evidence` (list of source
   references), `dataClassification` (string, e.g. `INTERNAL`), and
   `expiresAt` (ISO-8601 instant or null).
2. THE `shared-mcp-contracts` library SHALL declare a compile-scope dependency
   on the `AGENT_TOOL_PROTOCOL` MCP SDK so that consuming service modules
   inherit the MCP server starter via the `Parent_Build_Descriptor` dependency
   management.
3. THE `shared-mcp-contracts` library SHALL define a `ToolRisk` enum with
   values `L`, `M`, `H` and a `@GatedTool` annotation that marks `MCPTool`
   methods with `ToolRisk` M or H for enforcement by the gateway.
4. EVERY field in `ToolEnvelope` SHALL have field-level Javadoc referencing
   its contract definition.
5. THE `shared-mcp-contracts` library SHALL use only `SyntheticData` in all
   test fixtures and documentation examples.

---

### Requirement 2: Per-Service MCP Server Configuration

**User Story:** As an agent developer, I want each `Service_Module` to expose
its tools via a `MCPServer` so that the `AGENT_PLATFORM` can call any service
tool through a single, consistent MCP protocol.

#### Acceptance Criteria

1. EVERY `Service_Module` in `Middleware/` (except `shared-mcp-contracts` and
   `shared-domain-contracts`) SHALL add the `AGENT_TOOL_PROTOCOL` MCP server
   starter as a compile-scope dependency and register a `MCPServer` bean in
   its `SERVICE_FRAMEWORK` configuration.
2. THE `MCPServer` in each service SHALL be configured with a server name
   matching the service's `spring.application.name` (e.g.
   `trade-lifecycle-service`), so that tool discovery identifies the owning
   service.
3. EACH service's `MCPServer` SHALL expose its tools on a dedicated port
   (distinct from the REST API port) to avoid routing conflicts; the MCP port
   SHALL be externalized as configuration (inherited GP-Rq-11).
4. THE `MCPServer` SHALL register only tools explicitly annotated with
   `@MCPTool` (or equivalent `AGENT_TOOL_PROTOCOL` annotation); helper or
   internal methods SHALL NOT be auto-registered.
5. WHEN the `MCPServer` fails to start (e.g. port conflict), THE
   `Service_Module` SHALL fail to start entirely (fast-fail, not degraded
   mode) so that tool unavailability is immediately visible.

---

### Requirement 3: Tool Registration Conventions

**User Story:** As an agent developer, I want a consistent tool-naming and
input/output convention so that I can call any platform tool without learning
a different schema for each service.

#### Acceptance Criteria

1. EVERY `MCPTool` name SHALL follow the pattern `{verb}{Entity}` in
   camelCase (e.g. `getTrade`, `getTradeEvents`, `startReconciliation`),
   where the verb is one of: `get` (read, Risk L), `list` (read collection,
   Risk L), `check` (read/validate, Risk L), `start` (M/H — gated), `replay`
   (M/H — gated), `quarantine` (M — gated).
2. EVERY `MCPTool` input SHALL be a typed DTO with field-level validation (per
   the `BEAN_VALIDATION` role); the `MCPServer` SHALL return a structured
   validation error if the input fails validation, never a raw exception.
3. EVERY `MCPTool` output SHALL be wrapped in a `ToolEnvelope`; the tool SHALL
   populate `businessEntity`, `status`, `facts`, and `permittedActions`
   accurately — `permittedActions` MUST be sourced from the deterministic
   service (e.g. State Reconciliation) and SHALL NOT be authored by an LLM.
4. `GatedTool` methods (Risk M or H) SHALL validate that the inbound request
   carries a non-null `approvalReference` before executing any side effect;
   if missing, they SHALL return a `ToolEnvelope` with `status = FAILURE` and
   a `violations` entry `APPROVAL_REFERENCE_REQUIRED`.
5. ALL `MCPTool` input and output types SHALL use `SyntheticData` in
   documentation and test examples.

---

### Requirement 4: Initial Tool Set per Service

**User Story:** As an agent developer, I want the minimum viable tool set
registered for each service so that the MVP agents (Trade Lifecycle, DLQ
Triage, Supervisor) can be implemented end-to-end.

#### Acceptance Criteria

1. THE `trade-lifecycle-service` MCPServer SHALL expose at minimum:
   `getTrade(tradeId)` → `ToolEnvelope` (Risk L),
   `getTradeEvents(tradeId)` → `ToolEnvelope` (Risk L),
   `getTradeTimeline(tradeId)` → `ToolEnvelope` (Risk L).
2. THE `state-reconciliation-service` MCPServer SHALL expose at minimum:
   `evaluateCanonicalState(tradeId)` → `ToolEnvelope` (Risk L),
   `startReconciliation(tradeId, approvalReference)` → `ToolEnvelope` (Risk M — gated).
3. THE `risk-calculation-service` MCPServer SHALL expose at minimum:
   `getRiskResult(tradeId)` → `ToolEnvelope` (Risk L),
   `getRuleTrace(calculationId)` → `ToolEnvelope` (Risk L).
4. THE `eod-processing-service` MCPServer SHALL expose at minimum:
   `getRegionalCloseStatus(regionCode)` → `ToolEnvelope` (Risk L),
   `getReadinessStatusMap()` → `ToolEnvelope` (Risk L).
5. THE `business-calendar-service` MCPServer SHALL expose at minimum:
   `getBusinessCalendar(regionCode)` → `ToolEnvelope` (Risk L),
   `classifyBookingDate(regionCode, instant)` → `ToolEnvelope` (Risk L).

---

### Requirement 5: Local MCP Gateway Configuration

**User Story:** As an agent developer, I want a single MCP gateway
configuration registered in the `AGENT_PLATFORM` that lists all service
`MCPServer` endpoints so that agents discover all tools without per-service
configuration.

#### Acceptance Criteria

1. THE `DevOps/Local/` setup SHALL include an MCP gateway configuration file
   under `DevOps/Local/AGENT_PLATFORM/mcp-servers.json` that lists every
   `Service_Module`'s `MCPServer` endpoint (host, port, server name).
2. THE `AGENT_PLATFORM` local compose configuration SHALL mount
   `mcp-servers.json` so that the `AGENT_PLATFORM` instance reads it at
   startup and makes all listed tools discoverable to agents.
3. THE gateway configuration SHALL use the `CONTAINER_RUNTIME` service names
   (not `localhost`) as hostnames so that service-to-service communication
   works within the compose network.
4. WHEN a new `Service_Module` adds a `MCPServer`, the `mcp-servers.json`
   SHALL be updated in the same pull request.
5. THE `mcp-servers.json` SHALL NOT contain any credentials, API keys, or
   secrets; credential references shall use environment variable placeholders
   only.
