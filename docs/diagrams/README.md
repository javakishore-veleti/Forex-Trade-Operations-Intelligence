# Architecture Diagrams

All diagrams use Mermaid format — they render natively on GitHub without any tooling.

## Diagrams

| Diagram | File | Description |
|---------|------|-------------|
| System Context | [system-context.md](system-context.md) | C4 Level 1 — external actors and system boundary |
| Container Architecture | [container-architecture.md](container-architecture.md) | C4 Level 2 — all containers, stores, and relationships |
| Trade Lifecycle Flow | [trade-lifecycle-flow.md](trade-lifecycle-flow.md) | End-to-end data flow from capture through settlement |
| Agent Architecture | [agent-architecture.md](agent-architecture.md) | Agent triggers, routing, MCP tools, HITL gates |
| Local Infrastructure | [local-infrastructure.md](local-infrastructure.md) | Docker Compose services, ports, and networks |

## Conventions

- **Format**: Mermaid (text-as-code, Git-diffable, GitHub-native rendering)
- **C4 Model**: System Context → Container → Component (not all levels needed for every view)
- **Identifiers**: All use synthetic `FX-` prefix
- **No binary files**: No .drawio, .pptx, .vsdx committed (Mermaid covers all needs)
- **Updates**: When architecture changes, update the diagram in the same PR
