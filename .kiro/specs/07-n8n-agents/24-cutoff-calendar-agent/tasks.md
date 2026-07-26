# Tasks — Cutoff & Calendar Enforcement Agent Workflow Implementation

## Workflow File
`Agents/workflows/specialized/cutoff-calendar.workflow.json`

---

## Tasks

- [ ] Task 1: Create workflow skeleton with metadata (`name: "Cutoff Calendar Enforcement Agent"`, `settings`, `active`, `id`)
- [ ] Task 2: Add Webhook Trigger node — POST `/webhook/cutoff-calendar`, receives `region`, `correlationId`
- [ ] Task 3: Add Get Regional Cutoff node — HTTP GET to calendar-mcp `getRegionalCutoff(region)`
- [ ] Task 4: Add Get Post-Cutoff Events node — HTTP GET to calendar-mcp `getPostCutoffEvents()`
- [ ] Task 5: Add Classify Booking Date node — HTTP POST to calendar-mcp `classifyBookingDate()` for each event
- [ ] Task 6: Add Get Approaching Cutoff node — HTTP GET to calendar-mcp `getTradesApproachingCutoff()`
- [ ] Task 7: Add Cutoff Analyzer LLM node — Opus-class, produces cutoff report with hold recommendations
- [ ] Task 8: Add Hold Check IF node — branches if post-cutoff trades exist
- [ ] Task 9: Add Hold Approval Wait node — HITL gate for hold, 2h timeout
- [ ] Task 10: Add Approval Handler IF node — branches approved/denied
- [ ] Task 11: Add Hold For Next Day node — HTTP POST to trade-lifecycle-mcp `holdForNextDay()`
- [ ] Task 12: Add Respond to Webhook node — returns cutoff report
- [ ] Task 13: Wire all connections including parallel fetches and HITL branch
- [ ] Task 14: Set node positions for visual layout in n8n editor

## Verification

- [ ] JSON is valid: `python3 -c "import json; json.load(open('Agents/workflows/specialized/cutoff-calendar.workflow.json'))"`
- [ ] Contains required top-level fields: `name`, `nodes`, `connections`, `settings`
- [ ] HITL gate blocks hold execution
- [ ] All node types use valid n8n type identifiers
- [ ] Connections reference existing node names
