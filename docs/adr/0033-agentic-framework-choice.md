# ADR-0033: Agentic AI Framework — n8n vs LangChain vs CrewAI vs AutoGen

## Status
Accepted

## Context
The platform requires an agent orchestration layer that: hosts 34 agents, coordinates multi-agent workflows, integrates with MCP tools, supports human-in-the-loop gates, provides memory/session management, handles webhook triggers from sidecars, and runs reliably in both local and cloud environments.

## Decision
Use **n8n** as the sole `AGENT_PLATFORM` for all AI agents.

**Why n8n:**
- **Visual workflow authoring** — agent logic is inspectable without reading code
- **Native tool-use agent node** (`@n8n/n8n-nodes-langchain.agent`) with built-in MCP client support
- **Wait node for HITL** — native pause/resume with webhook-based approval; no custom infrastructure
- **Multiple LLM providers** — switch models per-node without code changes
- **Workflow-as-tool** — one agent can call another agent's workflow as a sub-tool
- **Webhook and schedule triggers** — sidecars POST to webhooks; canary probes run on cron
- **Queue mode workers** — horizontally scalable execution for production workloads
- **JSON export/import** — workflows are version-controlled as JSON in Git
- **Self-hosted** — runs locally in Docker and in cloud (EKS/AKS); no vendor SaaS dependency

## Alternatives Considered

### LangChain / LangGraph
- **Pros**: Rich Python ecosystem, fine-grained control, extensive model support
- **Cons**: Code-based (not visual), requires Python runtime for agents (violates our Java-services/Python-sidecars-only boundary), HITL requires custom implementation, no native workflow persistence, debugging is print-statement-based
- **Rejected because**: Agents would be Python code, blurring the language boundary. Operations staff can't inspect/modify agent logic without Python expertise.

### CrewAI
- **Pros**: Multi-agent patterns built-in, role-based agents
- **Cons**: Python-only, opinionated memory model that doesn't integrate with our Redis/Postgres strategy, no native HITL gate, no webhook triggers, limited production deployment story
- **Rejected because**: Production readiness concerns; no native HITL; Python-only.

### AutoGen / Semantic Kernel
- **Pros**: Microsoft-backed, strong multi-agent conversation patterns
- **Cons**: Heavy .NET or Python dependency, designed for conversational agents not operational workflows, no native MCP support, HITL requires custom middleware
- **Rejected because**: Not designed for operational automation; poor fit for webhook-triggered, tool-calling, approval-gated workflows.

### Custom Spring Boot agent framework
- **Pros**: Full control, same language as services, could embed agent logic in microservices
- **Cons**: Massive build effort, reinventing memory/routing/HITL/multi-agent; agents become coupled to service deployments; no visual debugging
- **Rejected because**: Build cost is unjustifiable when n8n provides the needed capabilities out of the box.

## Consequences
- All agent logic is in n8n workflow JSON — inspectable, version-controlled, modifiable by non-developers
- Language boundary maintained: Java for services, Python for detection, n8n for agents
- HITL gates are first-class (Wait node), not afterthoughts
- Trade-off: n8n's LLM node is less flexible than raw LangChain chains for complex prompt engineering; mitigated by using HTTP Request nodes with custom prompts when needed
- Vendor risk: n8n is open-source (fair-code license); self-hosted eliminates SaaS dependency
