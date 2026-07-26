# ADR-0007: Multi-Tool AI Development Strategy

## Status
Accepted

## Context
This project uses multiple AI development tools: Kiro CLI, Kiro IDE, and Claude Code CLI. Each has different strengths but all operate on the same codebase.

## Decision
- **`.kiro/specs/`** is the shared source of truth — all tools read and write it
- **Kiro CLI** for spec management, quality audit, and status tracking
- **Kiro IDE** for visual spec navigation and sub-agent task execution
- **Claude Code CLI** for bulk code generation and parallel work
- Specs are plain markdown files in Git — tool-agnostic by design
- `MASTER-PLAN.md` is the universal progress tracker
- Hooks (`.kiro/hooks/`) fire on file save regardless of which tool writes the file

## Consequences
- No vendor lock-in to any single AI tool
- Work can be parallelized across tools
- Status is always in Git (reviewable, diffable)
- Any tool can resume work from where another left off by reading tasks.md
