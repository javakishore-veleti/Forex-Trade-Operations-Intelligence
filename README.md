# Forex-Trade-Operations-Intelligence

[![Java 21](https://img.shields.io/badge/Java-21_LTS-orange?logo=openjdk&logoColor=white)](https://openjdk.org/projects/jdk/21/)
[![Spring Boot 3.4](https://img.shields.io/badge/Spring_Boot-3.4-6DB33F?logo=spring-boot&logoColor=white)](https://spring.io/projects/spring-boot)
[![Angular 19](https://img.shields.io/badge/Angular-19-DD0031?logo=angular&logoColor=white)](https://angular.dev/)
[![n8n](https://img.shields.io/badge/n8n-Agents-FF6D5A?logo=n8n&logoColor=white)](https://n8n.io/)
[![Python 3.11+](https://img.shields.io/badge/Python-3.11+-3776AB?logo=python&logoColor=white)](https://www.python.org/)
[![PostgreSQL 16](https://img.shields.io/badge/PostgreSQL-16-4169E1?logo=postgresql&logoColor=white)](https://www.postgresql.org/)
[![Apache Kafka](https://img.shields.io/badge/Kafka-3.x-231F20?logo=apache-kafka&logoColor=white)](https://kafka.apache.org/)
[![Redis 7](https://img.shields.io/badge/Redis-7-DC382D?logo=redis&logoColor=white)](https://redis.io/)
[![MongoDB 7](https://img.shields.io/badge/MongoDB-7-47A248?logo=mongodb&logoColor=white)](https://www.mongodb.com/)
[![Drools 9](https://img.shields.io/badge/Drools-9-1E8CBE?logo=drools&logoColor=white)](https://www.drools.org/)
[![License: MIT](https://img.shields.io/badge/License-MIT-yellow.svg)](LICENSE)
[![Spec-Driven](https://img.shields.io/badge/Methodology-Spec_Driven_Development-blueviolet)](docs/adr/)
[![Synthetic Data Only](https://img.shields.io/badge/Data-Synthetic_Only_(FX--prefix)-green)](README.md#synthetic-data-policy)

---

Forex-Trade-Operations-Intelligence is a spec-driven, publicly available reference implementation of a runtime-intelligence platform for foreign-exchange trade operations.

## Top-Level Directory Structure

| Directory | Role |
|-----------|------|
| `Middleware/` | Java 21 / Spring Boot microservices — parent Maven POM and all service modules |
| `Portals/` | Three Angular 19 standalone portal applications (Admin, TraderDesk, FXTradeBlotter) |
| `Agents/` | n8n workflow JSON exports only — supervisor, specialized, and utility agent workflows |
| `Sidecars/` | Python detection and embedding sidecar packages (statistical analysis, not business logic) |
| `DevOps/` | Infrastructure-as-code for local and future cloud environments |
| `docs/` | Architecture Decision Records (ADRs), diagrams, and design documentation |
| `.github/` | GitHub configuration, CODEOWNERS, and CI workflow placeholders |
| `scripts/` | Utility scripts for the monorepo |

## Architectural Constraints

See [docs/adr/0001-monorepo-language-boundaries.md](docs/adr/0001-monorepo-language-boundaries.md) for the foundational decision on language and tier boundaries across the platform.

## Synthetic Data Policy

All examples, identifiers, and test data in this repository use synthetic `FX-` prefixed identifiers (e.g., FX-000001). No real financial institution, person, or confidential data is committed.
