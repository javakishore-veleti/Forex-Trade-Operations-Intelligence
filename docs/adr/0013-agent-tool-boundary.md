# ADR-0013: Agent Tool Boundary — MCP Protocol

**Status:** Accepted

**Date:** 2024-02-14

## Context

Agents need to invoke platform capabilities (query trade state, retrieve risk calculations, trigger reconciliation). The boundary between agent workflows and backend services must be well-defined, secure, and auditable.

Three approaches were evaluated:

1. **MCP (Model Context Protocol)** — standardized tool-calling protocol with typed schemas, capability negotiation, and structured responses.
2. **Direct REST calls** — agents call service REST endpoints directly via n8n HTTP nodes.
3. **LLM function calling** — tools defined as function schemas within the LLM call; LLM generates structured call parameters.

## Decision

We adopt **MCP protocol** as the canonical agent-to-service tool boundary, exposed through a dedicated MCP server layer (Spring AI).

### Implementation

- A `mcp-server` Spring Boot application exposes platform tools as MCP-compliant endpoints.
- Each tool has a typed schema: name, description, input parameters (JSON Schema), output envelope.
- n8n agent workflows call MCP tools via HTTP nodes to the MCP server, not directly to individual microservices.
- The MCP server handles: authentication, rate limiting, input validation, audit logging, and routing to the appropriate backend service.

### Tool Registration Example

```
Tool: get-trade-lifecycle
Description: Retrieves the full lifecycle event sequence for a given trade ID
Input: { "tradeId": "FX-004521" }
Output: { "events": [...], "currentState": "SETTLED", "duration_ms": 45200 }
```

### Routing Flow

Agent → n8n HTTP Node → MCP Server → trade-lifecycle-service → response → agent

## Alternatives Considered

| Alternative | Reason Rejected |
|-------------|-----------------|
| Direct REST | Agents would need knowledge of individual service URLs, auth tokens, and API versions; no centralized audit |
| LLM function calling | Ties tool definitions to LLM provider's format; no server-side validation before execution; harder to audit |
| GraphQL gateway | Over-engineered for tool-calling pattern; agents need discrete actions, not flexible queries |

## Consequences

### Positive
- Single entry point for all agent-to-service communication — simplifies security and audit
- Typed schemas prevent malformed tool calls from reaching backend services
- MCP server can enforce risk-level gating (M/H-risk tools require prior HITL approval)
- Provider-agnostic — works with any LLM or orchestration engine

### Negative
- MCP server is an additional hop (adds ~5-15ms latency per tool call)
- Tool schema maintenance — new service capabilities require MCP tool registration
- MCP protocol is relatively new; tooling ecosystem still maturing

### Mitigations
- MCP server is co-located with backend services (same network); latency impact minimal
- Tool schemas are generated from OpenAPI specs of backend services at build time
- MCP server implementation uses Spring AI's MCP support — well-maintained framework
