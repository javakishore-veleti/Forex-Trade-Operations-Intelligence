# state-reconciliation-service

Reconciles distributed state by consuming the Kafka event stream. Detects inconsistencies across services by comparing event-sourced state with the current state of each participating service.

## Technology

- Java 21
- Spring Boot
- Spring Kafka
- Maven

## Configuration

- `spring.application.name`: `state-reconciliation-service`

## EVENT_STREAM Dependency

This is the only Phase-0 module that declares the `EVENT_STREAM` (Spring Kafka) dependency. It reads the Kafka event stream at the reconciliation layer to detect state drift between services. Other services do not declare this dependency until their feature specifications justify it.

## How to Run

```bash
mvn spring-boot:run
```

## How to Test

```bash
mvn test
```
