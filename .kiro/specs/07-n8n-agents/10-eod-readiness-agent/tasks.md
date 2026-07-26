# Tasks — End-of-Day Risk Readiness Agent Workflow Implementation

## Workflow Files
- `Agents/workflows/specialized/eod-readiness-global.workflow.json`
- `Agents/workflows/specialized/eod-readiness-regional.workflow.json`

---

## Tasks (Global Supervisor Workflow)

- [ ] Task 1: Create global workflow skeleton with metadata (`name: "EOD Readiness - Global Supervisor"`)
- [ ] Task 2: Add Webhook Trigger node — POST `/webhook/eod-readiness`, receives `regions[]`, `correlationId`
- [ ] Task 3: Add APAC Sub-Agent node — HTTP POST to regional sub-workflow with `region: "APAC"`
- [ ] Task 4: Add EMEA Sub-Agent node — HTTP POST to regional sub-workflow with `region: "EMEA"`
- [ ] Task 5: Add AMER Sub-Agent node — HTTP POST to regional sub-workflow with `region: "AMER"`
- [ ] Task 6: Add Global Synthesizer LLM node — Opus-class, produces ReadinessMap with go/no-go
- [ ] Task 7: Add Blockers Check IF node — branches on blockers present in map
- [ ] Task 8: Add Exception HITL Wait node — pauses for exception approval
- [ ] Task 9: Add Exception Handler IF node — branches approved/denied
- [ ] Task 10: Add Consolidation HITL Wait node — pauses for consolidation approval
- [ ] Task 11: Add Consolidation Handler IF node — branches approved/denied
- [ ] Task 12: Add Start Consolidation node — HTTP POST to eod-processing-mcp `startGlobalConsolidation()`
- [ ] Task 13: Add Respond to Webhook node — returns ReadinessMap
- [ ] Task 14: Wire all connections including HITL branches
- [ ] Task 15: Set node positions for visual layout

## Tasks (Regional Sub-Agent Workflow)

- [ ] Task 16: Create regional workflow skeleton (`name: "EOD Readiness - Regional"`)
- [ ] Task 17: Add Webhook Trigger node — receives `{ region }`
- [ ] Task 18: Add Get Close Status node — HTTP GET `getRegionalCloseStatus(region)`
- [ ] Task 19: Add Get Unprocessed Count node — HTTP GET `getUnprocessedTradeCount(region)`
- [ ] Task 20: Add Get Late Materiality node — HTTP GET `getLateTradeMateriality(region)`
- [ ] Task 21: Add Get Market Data Readiness node — HTTP GET `getMarketDataReadiness(region)`
- [ ] Task 22: Add Get Branch Completion node — HTTP GET `getBranchCompletionStatus(region)`
- [ ] Task 23: Add Regional Assessor LLM node — Sonnet-class, outputs READY/WARNING/BLOCKED
- [ ] Task 24: Add Respond to Webhook node — returns regional status
- [ ] Task 25: Wire regional workflow connections
- [ ] Task 26: Set regional node positions

## Verification

- [ ] Both JSON files are valid
- [ ] Global workflow calls regional sub-workflow endpoints
- [ ] HITL gates correctly block consolidation start
- [ ] Regional workflow queries all 5 MCP tools
- [ ] All connections reference existing node names
