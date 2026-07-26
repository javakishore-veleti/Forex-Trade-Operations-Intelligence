# Agent Architecture

```mermaid
flowchart TB
    subgraph Triggers
        W[Webhook from Sidecar]
        S[Schedule: every 5 min]
        U[User via Admin Portal]
    end

    subgraph Portals["Portals (Angular 19)"]
        ADM[Admin Portal<br/>Approval Inbox]
        TD[TraderDesk Portal<br/>Trade Status + Risk Explanation]
        BLT[FX Blotter Portal<br/>Position + Settlement]
    end

    subgraph n8n["n8n Agent Platform"]
        SUP[Supervisor Agent<br/>Intent Classification + Routing]
        
        subgraph Specialized["Specialized Agents"]
            TL[Trade Lifecycle<br/>Reconstruction]
            DR[DLQ Triage<br/>Replay/Quarantine]
            CP[Canary Probe<br/>Synthetic Liveness]
            RE[Risk Explainability<br/>Factor Breakdown]
            EOD[EOD Readiness<br/>Regional Status]
            OTH[... 29 more agents]
        end

        HITL[HITL Gate<br/>Wait Node — pauses execution]
    end

    subgraph MCP["MCP Tool Layer (Spring AI)"]
        MCP1[getTrade / getTradeEvents]
        MCP2[evaluateCanonicalState / startReconciliation]
        MCP3[getRiskResult / getRuleTrace]
        MCP4[getRegionalCloseStatus / approveException]
        MCP5[classifyBookingDate / injectSyntheticTrade]
    end

    subgraph Services["Spring Boot Services (Java 21)"]
        SVC1[trade-lifecycle-service]
        SVC2[state-reconciliation-service]
        SVC3[risk-calculation-service]
        SVC4[eod-processing-service]
        SVC5[business-calendar-service]
    end

    subgraph Infra["Infrastructure"]
        PG[(PostgreSQL)]
        KAFKA[Kafka]
        MONGO[(MongoDB)]
        REDIS[(Redis)]
        VEC[(pgvector)]
    end

    %% Trigger flows
    W --> DR
    W --> RE
    S --> CP
    U --> SUP

    %% Supervisor routing
    SUP --> TL
    SUP --> DR
    SUP --> RE
    SUP --> EOD

    %% Agent → MCP tools
    TL --> MCP1
    DR --> MCP2
    RE --> MCP3
    EOD --> MCP4
    CP --> MCP5

    %% MCP → Services
    MCP1 --> SVC1
    MCP2 --> SVC2
    MCP3 --> SVC3
    MCP4 --> SVC4
    MCP5 --> SVC5

    %% Services → Infrastructure
    SVC1 & SVC2 & SVC3 & SVC4 & SVC5 --> PG
    SVC1 & SVC2 & SVC3 --> KAFKA
    SVC2 --> MONGO
    SVC1 & SVC2 --> REDIS

    %% HITL approval flow (the key connection)
    DR -.->|Risk M: propose action| HITL
    EOD -.->|Risk M: approve exception| HITL
    TL -.->|Risk M/H: propose replay| HITL
    HITL -->|webhook callback| ADM
    ADM -->|Approve/Reject + approvalReference| HITL
    HITL -->|Approved → execute| MCP2

    %% Portal reads from services
    ADM -->|REST API| SVC1 & SVC2 & SVC3 & SVC4
    TD -->|REST API| SVC1 & SVC3
    BLT -->|REST API| SVC3 & SVC2

    %% Agent memory
    TL -.->|similar incidents| VEC
    DR -.->|known signatures| VEC
```

## Flow Summary

1. **Trigger** — sidecar webhook, schedule, or user action via Admin Portal
2. **Routing** — Supervisor classifies intent, routes to specialized agent
3. **Investigation** — Agent calls MCP tools to gather data from services
4. **Decision** — Agent reasons about the data (LLM), proposes action
5. **HITL Gate** — For Risk M/H: execution pauses, approval request sent to Admin Portal
6. **Approval** — Ops/Risk Manager reviews in Admin Portal Approval Inbox, clicks Approve/Reject
7. **Execution** — On approval: agent resumes, calls gated MCP tool with `approvalReference`
8. **Result** — Service executes the action, returns ToolEnvelope, agent reports outcome

## Key Principle
> Agents propose. Services execute. Humans approve. Portals visualize.
