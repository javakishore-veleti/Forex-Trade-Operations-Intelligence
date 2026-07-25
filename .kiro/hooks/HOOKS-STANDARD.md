# Kiro Hooks Standard — Forex Trade Operations Intelligence

> This document defines the **authoritative hooks standard** for this project.
> Every hook is defined here before being implemented. Do not add hooks to
> `.kiro/hooks/` without a corresponding entry in this document.

---

## What Are Kiro Hooks?

Kiro hooks are event-driven automations that fire on specific triggers within
the Kiro CLI agent session. They run shell commands or scripts automatically
when a defined condition is met (e.g. a file is saved, a spec stage completes).
They are **not** git hooks — they are Kiro session hooks.

**Key properties:**
- `trigger`: when the hook fires (`PostFileSave`, `PostToolUse`, etc.)
- `matcher`: a regex pattern that filters which files/events activate the hook
- `action`: the shell command to execute when triggered

---

## Hook Catalogue (Normative)

### Hook 1: `auto-commit-specs` ✅ (active)

**File:** `auto-commit-specs.json`
**Purpose:** Automatically stages and commits any spec file change
(requirements, design, tasks) to git so that spec evolution is fully
version-controlled without manual commits.

**Trigger:** `PostFileSave`
**Matcher:** `\.kiro/specs/.*\.md$`
**Fires when:** Any `.md` file under `.kiro/specs/` is saved.
**Action:** `git add .kiro/specs/` → compute changed files → `git commit`
with a message derived from the spec folder path.
**Idempotency:** Only commits if `git diff --cached` shows staged changes.

---

### Hook 2: `validate-spec-agnostic` ⬜ (to be implemented)

**File:** `validate-spec-agnostic.json`
**Purpose:** When a spec `requirements.md` is saved, scan it for product
names or version numbers that violate the technology-agnostic rule. Warn
if any are found so the violation is caught immediately, not at review time.

**Trigger:** `PostFileSave`
**Matcher:** `\.kiro/specs/.*/requirements\.md$`
**Fires when:** Any `requirements.md` under `.kiro/specs/` is saved.
**Action:** Run a grep against the saved file for known banned terms
(e.g. `Spring Boot`, `PostgreSQL`, `Redis`, `Angular`, `Kafka`, `3\.4\.`,
`Java 21`, `python:3\.`) and print a warning to the terminal if any are found.
**Does NOT block the save** — it warns only.

---

### Hook 3: `update-master-plan-progress` ⬜ (to be implemented)

**File:** `update-master-plan-progress.json`
**Purpose:** When a `tasks.md` is saved with all tasks checked (`[x]`),
automatically update the progress table in `MASTER-PLAN.md` to mark that
spec's code phase complete.

**Trigger:** `PostFileSave`
**Matcher:** `\.kiro/specs/.*/tasks\.md$`
**Fires when:** Any `tasks.md` under `.kiro/specs/` is saved.
**Action:** Check if all tasks in the file are `[x]`. If yes, identify the
spec folder name and update the corresponding row in `MASTER-PLAN.md`
progress table from `⬜` to `✅` for the code column.

---

### Hook 4: `guard-no-secrets` ⬜ (to be implemented)

**File:** `guard-no-secrets.json`
**Purpose:** When any file under `Middleware/`, `Agents/`, `Sidecars/`, or
`DevOps/` is saved, scan for patterns that look like secrets (passwords,
API keys, connection strings with credentials) and warn immediately.

**Trigger:** `PostFileSave`
**Matcher:** `^(Middleware|Agents|Sidecars|DevOps)/.*`
**Fires when:** Any file in the implementation directories is saved.
**Action:** Grep for patterns: `password\s*=\s*[^\$\{]`, `api.key\s*=`,
`secret\s*=\s*[^\$\{]`, `Bearer [A-Za-z0-9]`. Print a warning with the
file name and line number if found.
**Does NOT block the save** — it warns only.

---

### Hook 5: `sync-spec-status` ⬜ (to be implemented)

**File:** `sync-spec-status.json`
**Purpose:** When a `design.md` or `tasks.md` is **created** (not just saved),
update `MASTER-PLAN.md` to reflect that the design or tasks stage is now
present for that spec.

**Trigger:** `PostFileSave`
**Matcher:** `\.kiro/specs/.*/(design|tasks)\.md$`
**Fires when:** A new `design.md` or `tasks.md` is created under any spec folder.
**Action:** Identify the spec from the path, update the corresponding row
in the MASTER-PLAN progress table to mark the stage present.

---

## Implementation Order

Hooks are implemented in this priority order:

| Priority | Hook | Reason |
|---|---|---|
| 1 | `auto-commit-specs` | ✅ Already active — most critical for version control |
| 2 | `guard-no-secrets` | Safety first — catches accidental secret commits |
| 3 | `validate-spec-agnostic` | Quality gate — catches product-name violations immediately |
| 4 | `sync-spec-status` | Progress tracking — keeps MASTER-PLAN accurate |
| 5 | `update-master-plan-progress` | Automation — removes manual progress updates |

---

## Hook JSON Schema Reference

```json
{
  "version": "v1",
  "hooks": [
    {
      "name": "Human-readable hook name",
      "trigger": "PostFileSave",
      "matcher": "<regex pattern>",
      "action": {
        "type": "command",
        "command": "<shell command>"
      }
    }
  ]
}
```

**Rules:**
- One logical purpose per hook file. Do not combine unrelated hooks in one file.
- Commands must be idempotent — running twice must not cause side effects.
- Commands must not block indefinitely — use timeouts where appropriate.
- Hooks must not commit secrets, real data, or non-synthetic identifiers.
