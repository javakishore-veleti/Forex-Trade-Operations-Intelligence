# Tasks — End-of-Day Risk Readiness Agent Workflow Implementation

## Workflow File
`Agents/workflows/specialized/specialized-eod-readiness.workflow.json`

---

## Tasks (Global Supervisor Workflow)

- [x] Task 1: Create global workflow skeleton with metadata (`name: "EOD Readiness - Global Supervisor"`)
- [x] Task 2: Add Webhook Trigger node — POST `/webhook/eod-readiness`, receives `regions[]`, `correlationId`
- [x] Task 3: Add APAC Sub-Agent node — HTTP POST to regional sub-workflow with `region: "APAC"`
- [x] Task 4: Add EMEA Sub-Agent node — HTTP POST to regional sub-workflow with `region: "EMEA"`
- [x] Task 5: Add AMER Sub-Agent node — HTTP POST to regional sub-workflow with `region: "AMER"`
- [x] Task 6: Add Global Synthesizer LLM node — Opus-class, produces ReadinessMap with go/no-go
- [x] Task 7: Add Blockers Check IF node — branches on blockers present in map
- [x] Task 8: Add Exception HITL Wait node — pauses for exception approval
- [x] Task 9: Add Exception Handler IF node — branches approved/denied
- [x] Task 10: Add Consolidation HITL Wait node — pauses for consolidation approval
- [x] Task 11: Add Consolidation Handler IF node — branches approved/denied
- [x] Task 12: Add Start Consolidation node — HTTP POST to eod-processing-mcp `startGlobalConsolidation()`
- [x] Task 13: Add Respond to Webhook node — returns ReadinessMap
- [x] Task 14: Wire all connections including HITL branches
- [x] Task 15: Set node positions for visual layout

## Tasks (Regional Sub-Agent Workflow)

- [x] Task 16: Create regional workflow skeleton (`name: "EOD Readiness - Regional"`)
- [x] Task 17: Add Webhook Trigger node — receives `{ region }`
- [x] Task 18: Add Get Close Status node — HTTP GET `getRegionalCloseStatus(region)`
- [x] Task 19: Add Get Unprocessed Count node — HTTP GET `getUnprocessedTradeCount(region)`
- [x] Task 20: Add Get Late Materiality node — HTTP GET `getLateTradeMateriality(region)`
- [x] Task 21: Add Get Market Data Readiness node — HTTP GET `getMarketDataReadiness(region)`
- [x] Task 22: Add Get Branch Completion node — HTTP GET `getBranchCompletionStatus(region)`
- [x] Task 23: Add Regional Assessor LLM node — Sonnet-class, outputs READY/WARNING/BLOCKED
- [x] Task 24: Add Respond to Webhook node — returns regional status
- [x] Task 25: Wire regional workflow connections
- [x] Task 26: Set regional node positions

## Verification

- [x] JSON file is valid
- [x] Global workflow calls regional sub-workflow endpoints
- [x] HITL gates correctly block consolidation start
- [x] Regional workflow queries all MCP tools via global workflow regional calls
- [x] All connections reference existing node names
