# ADR-0002: Spec-Driven Development Methodology

## Status
Accepted

## Context
This project needed a methodology that ensures every feature is well-defined before implementation begins, supports AI-assisted development across multiple tools (Kiro, Claude Code, Kiro IDE), and produces auditable, reviewable artifacts in Git.

## Decision
Adopt the Kiro Spec-Driven Development (SDD) methodology:
- Every feature progresses through `requirements.md → design.md → tasks.md → code`
- All specs live in `.kiro/specs/` organized by phase and feature
- `MASTER-PLAN.md` tracks progress across all specs
- Requirements are technology-agnostic (reference Technology Roles)
- Design resolves roles to concrete products
- Tasks are atomic, ordered, and independently verifiable

## Consequences
- Higher upfront investment before code is written
- Clear traceability from requirement to implementation
- Any AI tool can pick up and continue work from the specs
- Specs are version-controlled and reviewable in PRs
- Changes to technology choices require editing only `01-technology-stack`
