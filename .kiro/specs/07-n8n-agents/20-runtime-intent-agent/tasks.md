# Tasks — Runtime Intent-Inference Agent Workflow Implementation

## Workflow File
`Agents/workflows/specialized/runtime-intent.workflow.json`

---

## Tasks

- [ ] Task 1: Create workflow skeleton with metadata (`name: "Runtime Intent-Inference Agent"`, `settings`, `active`, `id`)
- [ ] Task 2: Add Webhook Trigger node — POST `/webhook/runtime-intent`, receives `window`, `region`, `correlationId`
- [ ] Task 3: Add Fetch Recent Activity node — HTTP GET to observability-mcp `getRecentActivity(window)`
- [ ] Task 4: Add Behavioral Clustering node — HTTP POST to observability-mcp `classifyIntent()` with activity signals
- [ ] Task 5: Add Align to Business Goal node — HTTP GET to calendar-mcp `alignToBusinessGoal()` with window and region
- [ ] Task 6: Add Intent Classifier LLM node — Opus-class, classifies intent and produces suppression guidance
- [ ] Task 7: Add Build Intent Envelope Set node — structures final IntentEnvelope with intent, confidence, suppression
- [ ] Task 8: Add Respond to Webhook node — returns IntentEnvelope to caller
- [ ] Task 9: Wire connections: Trigger → Fetch Activity → parallel (Clustering, Goal Alignment) → LLM → Envelope → Response
- [ ] Task 10: Set node positions for visual layout in n8n editor

## Verification

- [ ] JSON is valid: `python3 -c "import json; json.load(open('Agents/workflows/specialized/runtime-intent.workflow.json'))"`
- [ ] Contains required top-level fields: `name`, `nodes`, `connections`, `settings`
- [ ] All node types use valid n8n type identifiers
- [ ] Connections reference existing node names
- [ ] Importable into n8n (structure validated)
