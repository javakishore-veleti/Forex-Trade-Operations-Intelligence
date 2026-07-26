# Tasks — Cutoff & Calendar Enforcement Agent Workflow Implementation

## Workflow File
`Agents/workflows/specialized/cutoff-calendar.workflow.json`

---

## Tasks

- [x] Task 1: Create workflow skeleton with metadata (`name: "Cutoff Calendar Enforcement Agent"`, `settings`, `active`, `id`)
- [x] Task 2: Add Webhook Trigger node — POST `/webhook/cutoff-calendar`, receives `region`, `correlationId`
- [x] Task 3: Add Get Regional Cutoff node — HTTP GET to calendar-mcp `getRegionalCutoff(region)`
- [x] Task 4: Add Get Post-Cutoff Events node — HTTP GET to calendar-mcp `getPostCutoffEvents()`
- [x] Task 5: Add Classify Booking Date node — HTTP POST to calendar-mcp `classifyBookingDate()` for each event
- [x] Task 6: Add Get Approaching Cutoff node — HTTP GET to calendar-mcp `getTradesApproachingCutoff()`
- [x] Task 7: Add Cutoff Analyzer LLM node — Opus-class, produces cutoff report with hold recommendations
- [x] Task 8: Add Hold Check IF node — branches if post-cutoff trades exist
- [x] Task 9: Add Hold Approval Wait node — HITL gate for hold, 2h timeout
- [x] Task 10: Add Approval Handler IF node — branches approved/denied
- [x] Task 11: Add Hold For Next Day node — HTTP POST to trade-lifecycle-mcp `holdForNextDay()`
- [x] Task 12: Add Respond to Webhook node — returns cutoff report
- [x] Task 13: Wire all connections including parallel fetches and HITL branch
- [x] Task 14: Set node positions for visual layout in n8n editor

## Verification

- [x] JSON is valid: `python3 -c "import json; json.load(open('Agents/workflows/specialized/cutoff-calendar.workflow.json'))"`
- [x] Contains required top-level fields: `name`, `nodes`, `connections`, `settings`
- [x] HITL gate blocks hold execution
- [x] All node types use valid n8n type identifiers
- [x] Connections reference existing node names
