# Middleware

Java 21 / Spring Boot microservice modules managed by a multi-module Maven parent POM.

---

## Parent POM Structure

`Middleware/pom.xml` is the **Parent Build Descriptor** (`packaging=pom`). It contains
no application code — its role is to centralize dependency versions and plugin
configuration so that no child module re-declares them.

**Coordinates:**

```
groupId    = com.fxtradeops
artifactId = fxtradeops-parent
version    = 0.1.0-SNAPSHOT
packaging  = pom
```

### Dependency Management

The parent imports the following BOMs and managed dependencies via
`<dependencyManagement>`:

| Dependency | Role | Import type |
|---|---|---|
| `spring-boot-dependencies` (Spring Boot BOM) | SERVICE_FRAMEWORK — transitively pins JUnit 5 and Spring MockMvc | BOM (`scope=import`, `type=pom`) |
| `spring-ai-bom` (Spring AI BOM) | AGENT_TOOL_PROTOCOL — MCP Server support (consumed in Phase 6) | BOM (`scope=import`, `type=pom`) |
| `testcontainers-bom` (Testcontainers BOM) | INTEGRATION_TEST_HARNESS | BOM (`scope=import`, `type=pom`) |
| `jqwik` | PROPERTY_TEST — property-based testing library | Managed dependency (`scope=test`) |

Child modules add starters/libraries without specifying versions.

### Plugin Management

Shared plugin configuration lives in `<pluginManagement>`:

| Plugin | Purpose |
|---|---|
| `maven-compiler-plugin` | Compiles source with `release=21` for source and bytecode compatibility |
| `spring-boot-maven-plugin` | Repackages runnable services into executable JARs (opted-in per service) |
| `maven-surefire-plugin` | Executes unit and context-load tests during the test phase |

---

## Java 21 Requirement

All modules target **Java 21 LTS** (`maven.compiler.release=21`).

**Prerequisites:**

- JDK 21 or later must be installed (e.g., Eclipse Temurin 21, Oracle JDK 21).
- Ensure `JAVA_HOME` points to a JDK 21+ installation.
- Maven 3.9.x or later is recommended.

You can verify your setup:

```bash
java -version   # must report 21+
mvn --version   # confirm Maven uses JDK 21+
```

---

## How to Add a Module

1. **Create the subdirectory** under `Middleware/`:

   ```bash
   mkdir -p Middleware/my-new-service/src/main/java/com/fxtradeops/mynewservice
   mkdir -p Middleware/my-new-service/src/main/resources
   mkdir -p Middleware/my-new-service/src/test/java/com/fxtradeops/mynewservice
   ```

2. **Add a child `pom.xml`** in the new subdirectory referencing the parent:

   ```xml
   <parent>
       <groupId>com.fxtradeops</groupId>
       <artifactId>fxtradeops-parent</artifactId>
       <version>0.1.0-SNAPSHOT</version>
       <relativePath>../pom.xml</relativePath>
   </parent>
   ```

3. **Register the module** in the parent `pom.xml` — add the directory name to the
   `<modules>` list:

   ```xml
   <modules>
       <!-- existing modules -->
       <module>my-new-service</module>
   </modules>
   ```

4. **Add the application entry point** (`@SpringBootApplication`), `application.yml`
   (`spring.application.name` in kebab-case), and a context-load test.

5. **Verify** the scaffold compiles:

   ```bash
   mvn -f Middleware/pom.xml verify
   ```

The scaffold is not considered complete until the module is listed in `<modules>` and
the build passes (Req 2.4).

---

## Initial Module List (Phase 0)

| Module | Type | Description |
|---|---|---|
| `shared-domain-contracts` | Library (no runnable app) | Shared DTOs including the `MCP_Tool_Contract` envelope (`requestId`, `businessEntity`, `status`, `facts`, `violations`, `permittedActions`, `evidence`, `dataClassification`, `expiresAt`). Compile-scope dependency for all services. |
| `trade-ingest-service` | Runnable service | Ingests incoming trade events into the platform. |
| `trade-lifecycle-service` | Runnable service | Manages trade state transitions through the trade lifecycle. |
| `risk-calculation-service` | Runnable service | Computes risk metrics for active trade positions. |
| `eod-processing-service` | Runnable service | End-of-day batch processing and settlement reconciliation. |
| `business-calendar-service` | Runnable service | Business day and holiday management for trade scheduling. |
| `state-reconciliation-service` | Runnable service | Reconciles platform state via the Kafka event stream. Only Phase-0 module with Spring Kafka dependency. |

---

## Build Commands

From the repository root:

```bash
# Validate the parent POM only (non-recursive)
mvn -N -f Middleware/pom.xml validate

# Compile all modules and run tests
mvn -f Middleware/pom.xml verify
```

From the `Middleware/` directory:

```bash
# Validate parent only
mvn -N validate

# Full build + tests
mvn verify
```

---

## Conventions

- All example identifiers use the synthetic `FX-` prefix (e.g., `FX-000001`).
- No credential values, production URLs, or real financial data are stored in this directory.
- Each service module must have its parent set to `fxtradeops-parent`; non-conforming modules will be rejected.
