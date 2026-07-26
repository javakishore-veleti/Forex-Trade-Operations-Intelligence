# Tasks — Trade Lifecycle Reconstruction Agent Workflow Implementation

## Workflow File
`Agents/workflows/specialized/specialized-trade-lifecycle-reconstruction.workflow.json`

---

## Tasks

- [x] Task 1: Create workflow skeleton with metadata (`name`, `settings`, `active`, `id`)
- [x] Task 2: Add Webhook Trigger node — POST `/webhook/trade-lifecycle`, receives `tradeId`, `correlationId`, `userId`
- [x] Task 3: Add Get Trade HTTP Request node — POST to `http://trade-lifecycle-service:8081/mcp/getTrade` with `tradeId`
- [x] Task 4: Add Error Check IF node — branches on getTrade response status (FAILURE → error response)
- [x] Task 5: Add Error Response node — respondToWebhook with "trade not found" error
- [x] Task 6: Add Get Trade Events HTTP Request node — POST to `http://trade-lifecycle-service:8081/mcp/getTradeEvents`
- [x] Task 7: Add Get Trade Timeline HTTP Request node — POST to `http://trade-lifecycle-service:8081/mcp/getTradeTimeline`
- [x] Task 8: Add Anomaly Detection LLM node — lightweight perception model with structured anomaly detection prompt
- [x] Task 9: Add Similar Failure Retrieval HTTP Request node — POST to vector store endpoint for top-3 similar cases
- [x] Task 10: Add Reasoning & Recommendation LLM node — deep reasoning model for probable cause and safe action recommendation
- [x] Task 11: Add Format Response Set node — shapes final AgentEnvelope with facts, violations, permittedActions, riskLevel
- [x] Task 12: Add Respond to Webhook node — returns AgentEnvelope response
- [x] Task 13: Wire all connections between nodes (sequential chain with error branch)
- [x] Task 14: Set realistic node positions (x, y coordinates) for visual layout

## Verification

- [x] JSON is valid: `python3 -c "import json; json.load(open('Agents/workflows/specialized/specialized-trade-lifecycle-reconstruction.workflow.json'))"`
- [x] Contains required top-level fields: `name`, `nodes`, `connections`, `settings`
- [x] All node types use valid n8n type identifiers
- [x] Sequential MCP tool call chain: getTrade → getTradeEvents → getTradeTimeline
- [x] Connections reference existing node names
