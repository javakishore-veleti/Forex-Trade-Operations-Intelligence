# ADR-0006: MCP Tool Protocol for Agent-Service Communication

## Status
Accepted

## Context
AI agents (n8n workflows) need to call Spring Boot service capabilities in a structured, typed, discoverable way. Direct REST calls lack schema discovery and consistent response shaping.

## Decision
- Spring AI MCP Server embedded in each microservice
- `shared-domain-contracts` defines the `ToolEnvelope` response contract: `{requestId, businessEntity, status, facts, violations, permittedActions, evidence, dataClassification, expiresAt}`
- Tools annotated with `@GatedTool(ToolRisk.M)` or `@GatedTool(ToolRisk.H)` require `approvalReference` before executing side effects
- `PermittedAction` is a fixed enum catalogue — agents may only select from it, never expand it
- MCP gateway configuration (`mcp-servers.json`) lists all service endpoints for agent discovery
- n8n acts as MCP client, discovering and calling tools

## Consequences
- Agents get structured, typed responses (not raw HTTP)
- Tool discovery is automatic via MCP protocol
- Risk-gated tools enforce human approval at the protocol level
- The permitted-action catalogue is auditable and never model-authored
