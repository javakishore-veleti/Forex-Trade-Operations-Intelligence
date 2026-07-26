# ADR-0008: Java Records for Immutable Domain Types in shared-domain-contracts

## Status
Accepted

## Context
The `shared-domain-contracts` module defines canonical domain types shared across all seven
microservices — `TradeId`, `CurrencyPair`, `MonetaryAmount`, `SettlementDate`, `RiskScore`, etc.
These types must be:

- Immutable (thread-safe, safe to cache, safe to publish on Kafka)
- Structurally equal (two `TradeId("FX-000001")` instances must be equal)
- Serializable to JSON without custom adapters
- Compact in source — the module defines 40+ types

Choosing the wrong representation inflates boilerplate, adds runtime mutability risk, or
introduces external dependencies into the most-shared module in the monorepo.

## Decision
All domain value objects and DTOs in `shared-domain-contracts` are **Java 21 records**.

```java
public record TradeId(String value) {
    public TradeId { Objects.requireNonNull(value, "tradeId must not be null"); }
}

public record MonetaryAmount(BigDecimal amount, Currency currency) {
    public MonetaryAmount { Objects.requireNonNull(amount); Objects.requireNonNull(currency); }
}
```

Key rules:
- Compact constructors validate invariants (null checks, format checks)
- No mutable fields — collections wrapped with `List.copyOf()` / `Map.copyOf()`
- Jackson serialization works out-of-the-box with `jackson-module-parameter-names`
- Records implement domain marker interfaces (`DomainEvent`, `ToolEnvelope`) where needed

## Alternatives Considered

| Alternative | Why rejected |
|-------------|-------------|
| **Traditional classes + manual equals/hashCode** | Massive boilerplate for 40+ types; easy to forget updating `equals` when adding a field |
| **Lombok @Value** | Adds annotation-processor dependency to the most-shared module; IDE/build issues across teams; records are language-native |
| **Kotlin data classes** | Would require Kotlin runtime on the classpath of all Java services; violates monorepo single-language-per-tier rule (ADR-0001) |

## Consequences

### Positive
- Zero-dependency immutability — no annotation processors, no runtime libraries
- Structural equality and `toString()` free with every record
- Pattern matching (`instanceof TradeId(var id)`) enables concise downstream processing
- Jackson maps constructor parameters directly — no `@JsonProperty` annotations needed

### Negative
- Records cannot extend a base class (Java limitation) — shared behavior uses interfaces + default methods
- No builder pattern by default — complex records (8+ fields) use a static factory or a builder record
- Reflection-based frameworks (some older Spring versions) required parameter-name retention (`-parameters` flag)

### Mitigations
- Maven compiler plugin sets `-parameters` globally in the parent POM
- Builder pattern provided via companion `*Builder` utility class where field count exceeds 6
