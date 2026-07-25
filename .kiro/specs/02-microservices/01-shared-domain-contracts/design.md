# Design Document — Shared Kernel (Domain Contracts)

> **Stage 2 of 3** (`requirements.md → design.md → tasks.md`). This document
> realizes `requirements.md` for the Shared Kernel. Unlike requirements (which
> are technology-agnostic), **design resolves Technology Roles to concrete
> products** per `01-initial-setup/01-technology-stack`. This is a shared
> library — no runtime framework, no HTTP endpoints, no persistence.

## 1. Overview

`shared-domain-contracts` is a plain Java library (no Spring Boot application
class). It defines every immutable domain type, enum, and value object shared
across all `Middleware/` services. Every service depends on it at compile scope.
It does **not** run; it is linked.

**Technology Role → concrete binding:**

| Technology Role | Concrete product | Use |
|---|---|---|
| `SERVICE_LANGUAGE` | Java 21 | library language |
| `SERVICE_BUILD_TOOL` | Maven 3.9.x | build, packaging |
| `SERIALIZATION` | Jackson 2.x (via Spring Boot BOM) | JSON serialization config |
| `BEAN_VALIDATION` | Jakarta Bean Validation 3.x + Hibernate Validator | constraint annotations + runtime |
| `UNIT_TEST_FRAMEWORK` | JUnit 5 (Jupiter) | test runner |

---

## 2. Module structure

Maven module `Middleware/shared-domain-contracts`, package root
`com.fxtradeops.domain`:

```
com.fxtradeops.domain/
  trade/
    TradeRecord.java          — record, immutable
    TradeStatus.java          — enum (11 constants)
    TradeDirection.java       — enum (BUY, SELL)
    CurrencyPair.java         — record, immutable
  event/
    TradeEvent.java           — record, immutable
    TradeEventType.java       — enum (15 constants)
  risk/
    RiskResult.java           — record, immutable
    RiskLevel.java            — enum (LOW, MEDIUM, HIGH, CRITICAL)
    ContributingFactor.java   — record, immutable
    RiskCalculationRequest.java — record, immutable
  reference/
    Region.java               — record, immutable
    RegionCode.java           — enum (APAC, EMEA, AMERICAS, GLOBAL)
    TradingBook.java          — record, immutable
    BookType.java             — enum (SPOT, FORWARD, SWAP, OPTION)
    Counterparty.java         — record, immutable
    CounterpartyType.java     — enum (BANK, CORPORATE, FUND, BROKER, INTERNAL)
  common/
    Money.java                — record, immutable
    AuditInfo.java            — record, immutable
    PageRequest.java          — record, immutable
    PageResponse.java         — generic record, immutable
  config/
    DomainObjectMapper.java   — static factory: correctly configured ObjectMapper
                                (ISO-8601 temporals, numeric decimals)
```

**`pom.xml` dependency scope:**
- `jackson-databind` + `jackson-datatype-jsr310` — compile scope
- `jakarta.validation-api` — compile scope
- `hibernate-validator` — **test scope only** (runtime validation needed only in tests)
- `junit-jupiter` — test scope

---

## 3. Type design decisions

### 3.1 Java records for all DomainTypes

All `DomainType`s are Java 21 `record`s. Records are:
- **Immutable by construction** — no setters, final fields (Req 8)
- **Auto-`equals`/`hashCode`/`toString`** — correct value semantics out of the box
- **Compact** — no boilerplate getter declarations needed

Constraint: records cannot extend other classes, which suits these value objects perfectly (no inheritance hierarchy needed).

### 3.2 BigDecimal for all monetary/risk amounts

All decimal amount fields (`notionalAmount`, `riskAmount`, `contributionAmount`, `amount` in `Money`) use `java.math.BigDecimal` with scale enforced at construction:

```java
// Money constructor enforcement
public Money {
    Objects.requireNonNull(amount, "amount must not be null");
    amount = amount.setScale(2, RoundingMode.HALF_UP); // fixed scale=2 (Req 6.4)
}
```

`@Positive` (Jakarta) enforces strictly positive values. `@Digits` enforces scale where needed.

### 3.3 Temporal types

| Field semantic | Java type | Jackson serialization |
|---|---|---|
| Instant (e.g. `createdAt`) | `java.time.Instant` | ISO-8601 string via `JavaTimeModule` |
| Date (e.g. `tradeDate`) | `java.time.LocalDate` | ISO-8601 date string `YYYY-MM-DD` |
| Time zone (e.g. `timezone` in `Region`) | `java.time.ZoneId` | IANA zone string via custom serializer |

`DomainObjectMapper` registers `JavaTimeModule` and sets
`WRITE_DATES_AS_TIMESTAMPS = false` globally (Req 9).

### 3.4 Payload map in TradeEvent

`TradeEvent.payload` is `Map<String, Object>`. Jackson deserializes this to
`LinkedHashMap<String, Object>` by default. Round-trip fidelity for primitive
values (String, Integer, Boolean) is guaranteed; complex nested objects are
deserialized as `LinkedHashMap`. Tests assert round-trip for the supported value
types (Req 10.3).

### 3.5 PageResponse generics

`PageResponse<T>` is a generic record. Jackson requires the element type at
deserialization time via `TypeReference` or `JavaType`. `DomainObjectMapper`
exposes a helper:

```java
public static <T> PageResponse<T> deserializePage(
        String json, Class<T> elementType) { … }
```

---

## 4. Validation design (Req 7)

Every record uses Jakarta Bean Validation annotations on its compact constructor
parameters. Validation is triggered by calling:

```java
Validator validator = Validation.buildDefaultValidatorFactory().getValidator();
Set<ConstraintViolation<TradeRecord>> violations = validator.validate(trade);
```

Key annotation mapping:

| Rule | Annotation |
|---|---|
| Required non-blank text | `@NotBlank` |
| Required non-null object | `@NotNull` |
| Strictly positive decimal | `@Positive` |
| Exactly 3 characters | `@Size(min=3, max=3)` |
| Integer ≥ 0 | `@Min(0)` |
| Integer ≥ 1 | `@Min(1)` |
| Integer 1–100 | `@Min(1) @Max(100)` |

`@Valid` is placed on nested record fields (e.g. `CurrencyPair` inside
`TradeRecord`) so that cascaded validation fires automatically.

---

## 5. Serialization design (Req 9, 10)

`DomainObjectMapper` is a static factory (not a Spring bean — this library has
no Spring context):

```java
public final class DomainObjectMapper {
    public static ObjectMapper create() {
        return new ObjectMapper()
            .registerModule(new JavaTimeModule())
            .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)
            .enable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)  // strict
            .setSerializationInclusion(JsonInclude.Include.NON_NULL);
    }
}
```

Consuming services import this and register it as their `@Bean ObjectMapper`,
ensuring all services use identical serialization configuration.

`ZoneId` serialization: custom `ZoneIdSerializer` / `ZoneIdDeserializer` pair
registered via `SimpleModule` — writes the IANA zone name string (e.g.
`"Asia/Singapore"`), reads it back via `ZoneId.of(string)`.

---

## 6. Build descriptor design (Req 1)

`Middleware/shared-domain-contracts/pom.xml`:
- `<parent>` → `Middleware/pom.xml`
- `<packaging>jar</packaging>` — plain library, no repackage
- **No** `spring-boot-maven-plugin` repackage goal
- `<artifactId>shared-domain-contracts</artifactId>`

Every consuming service adds:
```xml
<dependency>
  <groupId>com.fxtradeops</groupId>
  <artifactId>shared-domain-contracts</artifactId>
  <!-- version managed by parent BOM -->
</dependency>
```

---

## 7. Testing strategy (Req 11)

One test class per domain type. Each test class covers three concerns:

1. **Valid construction** — build with synthetic data, assert all accessors return
   supplied values.
2. **Validation rejection** — null/blank/out-of-range each required field,
   assert ≥ 1 `ConstraintViolation`.
3. **Serialization round-trip** — serialize to JSON via `DomainObjectMapper`,
   deserialize back, assert equality.

Test fixtures use only `SyntheticData`:
- `tradeId = "FX-000001"`
- `counterpartyId = "CP-AURORA-001"` (fictional)
- `tradingBookId = "BOOK-APAC-001"` (fictional)
- `sourceService = "trade-ingest-service"` (fictional)

`TestObjectMapperProvider` — a shared test utility class in `src/test/java/`
that returns a pre-configured `DomainObjectMapper.create()` instance, used by
all serialization round-trip tests (Req 10.5).

---

## 8. Requirement → design traceability

| Requirement | Satisfied by |
|---|---|
| Req 1 — packaging & boundaries | §2, §6 |
| Req 2 — Trade domain model | §2 (`trade/`), §3.1, §3.2 |
| Req 3 — TradeEvent domain model | §2 (`event/`), §3.4 |
| Req 4 — Risk domain model | §2 (`risk/`), §3.2 |
| Req 5 — Reference data model | §2 (`reference/`) |
| Req 6 — Common value objects | §2 (`common/`), §3.2 |
| Req 7 — Bean validation | §4 |
| Req 8 — Immutability | §3.1 |
| Req 9 — Serialization behavior | §5 |
| Req 10 — Round-trip guarantee | §5, §7 |
| Req 11 — Testing standards | §7 |
| Req 12 — Synthetic data | §7 (fixtures) |
