# ADR-0003: Golden Path NFR Inheritance

## Status
Accepted

## Context
With 7 microservices sharing identical cross-cutting concerns (correlation IDs, health probes, error envelopes, security, idempotency, observability, etc.), repeating these in every service spec creates drift risk and maintenance burden.

## Decision
Define all cross-cutting NFRs once in `architecture-golden-path/01-service-nfrs/requirements.md` as 14 numbered requirements (GP-Rq-1 through GP-Rq-14). Every microservice spec states "Inherits architecture-golden-path/01-service-nfrs" and contains ONLY business/domain requirements. A service spec restates a golden-path requirement only to narrow or override it with an explicit "overrides GP-Rq-N" note.

## Consequences
- Service specs are focused and concise (business logic only)
- NFR changes are single-file edits
- Every service's design.md shows a concrete NFR realization table
- Auditors can verify NFR compliance per service from the design doc
