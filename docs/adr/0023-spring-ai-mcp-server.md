# ADR-0023: Spring AI MCP Server for Tool Exposure

## Status
Accepted

## Context
AI agents (n8n workflows) need to invoke microservice capabilities as structured tools — e.g.,
"get trade lifecycle history for FX-000042" or "calculate risk impact for a proposed state change."
The tool interface must be:

- Discoverable (agent can list available tools without hardcoded URLs)
- Typed (input/output schemas defined, not free-form JSON)
- Risk-gated (M/H-risk tools require approval reference before execution)
- Consistent across all 7 services (same envelope, same error format)

The MCP (Model Context Protocol) standard provides exactly this: a protocol for exposing tools
to AI models with schema discovery, structured invocation, and typed responses.

## Decision
Embed a **Spring AI MCP Server** in each microservice, exposing service capabilities as MCP tools.

```java
@McpTool(name = "getTradeLifecycle", description = "Returns full lifecycle event history for a trade")
@GatedTool(ToolRisk.L)
public ToolEnvelope<LifecycleHistory> getTradeLifecycle(
    @McpParam(description = "Trade identifier, e.g. FX-000042") String tradeId) {
    // ... returns structured ToolEnvelope
}
```

Key properties:
- Each service registers its tools at startup; `mcp-servers.json` lists all service endpoints
- n8n acts as MCP client — discovers and invokes tools via the MCP protocol
- `ToolEnvelope` (from `shared-domain-contracts`) wraps every response with metadata:
  `{requestId, businessEntity, status, facts, violations, permittedActions, evidence, dataClassification}`
- `@GatedTool(ToolRisk.M)` requires `approvalReference` parameter — agent must obtain human approval first

## Alternatives Considered

| Alternative | Why rejected |
|-------------|-------------|
| **Custom REST-to-tool wrapper** | Re-invents tool discovery, schema negotiation, and risk gating; N services × M endpoints = large bespoke mapping layer; no standard for agent consumption |
| **LangChain tools (Python)** | Violates language boundary (ADR-0001); would require Python shim in front of Java services; runtime coupling between Python and Java |
| **OpenAPI spec as tool definition** | OpenAPI describes HTTP APIs, not tool semantics; lacks risk-gating, permitted-action catalogues, and structured envelopes; agents need more than "call this endpoint" |
| **Function-calling JSON schema only** | No discovery protocol; agent must be pre-configured with all tool schemas; adding a tool requires agent reconfiguration |

## Consequences

### Positive
- Tool discovery is automatic — new tools appear when service restarts with new `@McpTool` annotations
- Risk gating enforced at protocol level — agents cannot bypass approval for M/H tools
- Consistent response format (`ToolEnvelope`) across all services — agent parsing logic is uniform
- Spring AI integration is native — no additional framework or sidecar needed
- `permittedActions` catalogue constrains what agents can propose (never model-authored)

### Negative
- Spring AI MCP is relatively new — fewer community examples and potential API instability
- MCP protocol overhead vs raw REST is slightly higher (discovery handshake, envelope wrapping)
- All services must depend on `spring-ai-mcp-server` — adds to dependency footprint
- Debugging MCP interactions requires MCP-aware tooling (not standard HTTP debugging)

### Mitigations
- MCP server version pinned across all services in parent POM `<dependencyManagement>`
- Health endpoint verifies MCP server initialization; readiness probe gates traffic
- MCP request/response logged at DEBUG level for troubleshooting
- Fallback: tools are also exposed as standard REST endpoints for non-agent consumers
