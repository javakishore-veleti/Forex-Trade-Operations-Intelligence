# shared-domain-contracts

Shared library artifact containing the `MCP_Tool_Contract` DTO definitions used by all services in the platform. This module is a compile-scope dependency — it is **not** a runnable service.

## Purpose

Defines the common agent envelope schema (`MCP_Tool_Contract`) that every microservice uses when exposing capabilities via the Spring AI MCP Server protocol. The DTOs provide the shared contract between services and the n8n agent orchestration layer.

## MCP_Tool_Contract DTO Fields

| Field | Description |
|-------|-------------|
| `requestId` | Unique identifier for the request (e.g., `req-00001`) |
| `businessEntity` | The trade or entity identifier (e.g., `FX-000001`) |
| `status` | Current processing status of the request |
| `facts` | Collection of factual assertions derived from the data |
| `violations` | Rule violations detected during processing |
| `permittedActions` | Actions allowed given the current state |
| `evidence` | Supporting data elements backing the response |
| `dataClassification` | Sensitivity classification of the response payload |
| `expiresAt` | Timestamp after which this response should be considered stale |

## Technology

- Java 21
- Spring Boot (compile-scope dependency only, no runtime)
- Maven

## How to Build

```bash
mvn compile
```

This is a library artifact — there is no runnable application. It is consumed as a compile-scope dependency by all other services.

## How to Test

```bash
mvn test
```

## Synthetic Data Policy

All example identifiers in this module use synthetic values only:
- Trade IDs use the `FX-` prefix (e.g., `FX-000001`)
- Request IDs use the `req-` prefix (e.g., `req-00001`)

No real financial institution names, account numbers, or production data are used.
