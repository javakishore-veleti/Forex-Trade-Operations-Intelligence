# Kiro Hooks Standard — Forex Trade Operations Intelligence

> This document defines the **authoritative hooks standard** for this project.
> Every hook is defined here before being implemented. Do not add hooks to
> `.kiro/hooks/` without a corresponding entry in this document.

---

## What Are Kiro Hooks?

Kiro hooks are event-driven automations that fire on specific triggers within
the Kiro CLI/IDE agent session. They run shell commands automatically when a
defined condition is met (e.g. a file is saved). They are **not** git hooks —
they are Kiro session hooks.

**Key properties:**
- `trigger`: when the hook fires (`PostFileSave`)
- `matcher`: a regex pattern that filters which files activate the hook
- `action`: the shell command to execute when triggered

---

## Hook Catalogue (all active)

### Hook 1: `auto-commit-specs` ✅

**File:** `auto-commit-specs.json`
**Purpose:** Automatically stages and commits any spec file change to git so
that spec evolution is fully version-controlled without manual commits.
**Trigger:** `PostFileSave`
**Matcher:** `\.kiro/specs/.*\.md$`
**Action:** `git add .kiro/specs/` → commit with message derived from the
spec folder path.

---

### Hook 2: `validate-spec-agnostic` ✅

**File:** `validate-spec-agnostic.json`
**Purpose:** When a `requirements.md` is saved, scan for product names or
version numbers that violate the technology-agnostic rule. Warns immediately.
**Trigger:** `PostFileSave`
**Matcher:** `\.kiro/specs/.*/requirements\.md$`
**Action:** Grep for banned terms (Spring Boot, PostgreSQL, Redis, etc.) and
print a warning if found. Does NOT block the save.

---

### Hook 3: `update-master-plan-progress` ✅

**File:** `update-master-plan-progress.json`
**Purpose:** When a `tasks.md` is saved with all tasks checked `[x]`, print a
notification that the spec is fully implemented.
**Trigger:** `PostFileSave`
**Matcher:** `\.kiro/specs/.*/tasks\.md$`
**Action:** Count total `- [` lines and `- [x]` lines. If equal, print
"✅ All N tasks complete in {spec-name}".

---

### Hook 4: `guard-no-secrets` ✅

**File:** `guard-no-secrets.json`
**Purpose:** When any implementation file is saved, scan for patterns that
look like secrets (passwords, API keys, connection strings with credentials)
and warn immediately.
**Trigger:** `PostFileSave`
**Matcher:** `^(Middleware|Agents|Sidecars|DevOps)/.*`
**Action:** Grep for secret patterns. Print a warning with file name and line
number if found. Does NOT block the save.

---

### Hook 5: `sync-spec-status` ✅

**File:** `sync-spec-status.json`
**Purpose:** When a `design.md` or `tasks.md` is created or updated, print
a notification identifying which spec stage was saved.
**Trigger:** `PostFileSave`
**Matcher:** `\.kiro/specs/.*/(design|tasks)\.md$`
**Action:** Print "📋 Spec stage '{stage}' saved for: {spec-path}".

---

## Summary

| # | Hook | File | Status |
|---|---|---|---|
| 1 | auto-commit-specs | `auto-commit-specs.json` | ✅ active |
| 2 | validate-spec-agnostic | `validate-spec-agnostic.json` | ✅ active |
| 3 | update-master-plan-progress | `update-master-plan-progress.json` | ✅ active |
| 4 | guard-no-secrets | `guard-no-secrets.json` | ✅ active |
| 5 | sync-spec-status | `sync-spec-status.json` | ✅ active |

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
- Use `{{filePath}}` template variable for the saved file path.
