# Tasks — Shared Kernel (Domain Contracts)

> **Stage 3 of 3** (`requirements.md → design.md → tasks.md`). Derived from
> `design.md`. Execute top-to-bottom; each task is atomic, maps to specific
> files, and is independently verifiable. Mark `[x]` as each task is completed.
> Tags trace to design sections (§) and requirements (Req).

## 0. Module scaffold
- [x] 0.1 Create `Middleware/shared-domain-contracts/pom.xml` with `<parent>` → `Middleware/pom.xml`, `<packaging>jar</packaging>`, no `spring-boot-maven-plugin` repackage goal. (§6, Req 1)
- [x] 0.2 Add `shared-domain-contracts` to `<modules>` in `Middleware/pom.xml`. (Req 1.3)
- [x] 0.3 Create package tree: `com.fxtradeops.domain.{trade,event,risk,reference,common,config}` under `src/main/java/`. (§2)
- [x] 0.4 Create `src/test/java/com/fxtradeops/domain/` and `TestObjectMapperProvider.java` returning `DomainObjectMapper.create()`. (§7, Req 10.5)
- [x] 0.5 **Verify:** `mvn -pl Middleware/shared-domain-contracts compile` — compiles with zero errors.

## 1. Enums
- [x] 1.1 `trade/TradeStatus.java` — 11 constants: `CAPTURED, VALIDATED, ENRICHED, RISK_CALCULATED, BOOKED, ALLOCATED, CONFIRMED, SETTLED, CANCELLED, AMENDED, FAILED`. (Req 2.5)
- [x] 1.2 `trade/TradeDirection.java` — 2 constants: `BUY, SELL`. (Req 2.6)
- [x] 1.3 `event/TradeEventType.java` — 15 constants: `TRADE_CAPTURED, TRADE_VALIDATED, TRADE_ENRICHED, RISK_CALCULATION_REQUESTED, RISK_CALCULATION_COMPLETED, TRADE_BOOKED, TRADE_ALLOCATED, TRADE_CONFIRMED, TRADE_SETTLED, TRADE_CANCELLED, TRADE_AMENDED, TRADE_FAILED, EVENT_REPLAYED, PROCESSING_PAUSED, PROCESSING_RESUMED`. (Req 3.4)
- [x] 1.4 `risk/RiskLevel.java` — 4 constants: `LOW, MEDIUM, HIGH, CRITICAL`. (Req 4.5)
- [x] 1.5 `reference/RegionCode.java` — 4 constants: `APAC, EMEA, AMERICAS, GLOBAL`. (Req 5.4)
- [x] 1.6 `reference/BookType.java` — 4 constants: `SPOT, FORWARD, SWAP, OPTION`. (Req 5.8)
- [x] 1.7 `reference/CounterpartyType.java` — 5 constants: `BANK, CORPORATE, FUND, BROKER, INTERNAL`. (Req 5.12)
- [x] 1.8 **Verify:** `mvn -pl Middleware/shared-domain-contracts test-compile` — all enum classes compile.

## 2. Trade domain types (§2 `trade/`)
- [x] 2.1 `trade/CurrencyPair.java` — record with `@NotBlank String baseCurrency`, `@NotBlank String quoteCurrency`, `@NotBlank String pairCode`; `@Size(min=3,max=3)` on `baseCurrency` and `quoteCurrency`. (Req 2.7–2.9)
- [x] 2.2 `trade/TradeRecord.java` — record with all 15 fields per Req 2.1; `@NotBlank` on text IDs, `@NotNull` on object fields, `@Positive` on `notionalAmount`, `@Valid` on `currencyPair`, `version` field non-null `Long`. (Req 2.1–2.4, 2.10)
- [x] 2.3 Unit tests `CurrencyPairTest`: valid construction + `@NotBlank`/`@Size` violations + round-trip. **Verify:** tests green. (Req 11)
- [x] 2.4 Unit tests `TradeRecordTest`: valid construction + one null required field violation each + round-trip. **Verify:** tests green. (Req 11)

## 3. Event domain types (§2 `event/`)
- [x] 3.1 `event/TradeEvent.java` — record with `@NotBlank String eventId`, `@NotBlank String tradeId`, `@NotBlank String correlationId`, `@NotNull TradeEventType eventType`, `@NotNull Instant occurredAt`, `@NotNull Long sequenceNumber`, `@NotBlank String sourceService`, `@NotNull Map<String,Object> payload`. (Req 3.1–3.5)
- [x] 3.2 Unit tests `TradeEventTest`: valid construction + validation + round-trip including `payload` map. **Verify:** tests green. (Req 11)

## 4. Risk domain types (§2 `risk/`)
- [x] 4.1 `risk/ContributingFactor.java` — record with `@NotBlank String factorName`, `@Positive BigDecimal contributionAmount`, `@NotBlank @Size(min=3,max=3) String currency`. (Req 4.6–4.8)
- [x] 4.2 `risk/RiskResult.java` — record with all 9 fields per Req 4.1; `@NotBlank` on text IDs, `@NotNull` on objects, `@Positive` on `riskAmount`. (Req 4.1–4.4)
- [x] 4.3 `risk/RiskCalculationRequest.java` — record with all 7 fields per Req 4.9; `@NotBlank` on text IDs, `@NotNull` on objects, `@Min(1)` on `priority`. (Req 4.9–4.12)
- [x] 4.4 Unit tests `ContributingFactorTest`, `RiskResultTest`, `RiskCalculationRequestTest`: valid + validation + round-trip. **Verify:** tests green. (Req 11)

## 5. Reference data types (§2 `reference/`)
- [x] 5.1 `reference/Region.java` — record with `@NotNull RegionCode regionCode`, `@NotBlank String regionName`, `@NotNull ZoneId timezone`, `@NotBlank @Size(min=3,max=3) String baseCurrency`, `boolean isActive`. (Req 5.1–5.3)
- [x] 5.2 `reference/TradingBook.java` — record with `@NotBlank String bookId`, `@NotBlank String bookName`, `@NotNull RegionCode regionCode`, `@NotBlank String traderId`, `boolean isActive`, `@NotNull BookType bookType`. (Req 5.5–5.7)
- [x] 5.3 `reference/Counterparty.java` — record with `@NotBlank String counterpartyId`, `@NotBlank String counterpartyName`, `@NotNull CounterpartyType counterpartyType`, `@NotNull RegionCode regionCode`, `boolean isActive`, `@NotBlank String creditRating`. (Req 5.9–5.11)
- [x] 5.4 Unit tests `RegionTest`, `TradingBookTest`, `CounterpartyTest`: valid + validation + round-trip including `ZoneId`. **Verify:** tests green. (Req 11)

## 6. Common value objects (§2 `common/`)
- [x] 6.1 `common/Money.java` — record with `@NotNull @Positive BigDecimal amount`, `@NotBlank @Size(min=3,max=3) String currency`; compact constructor enforces `amount = amount.setScale(2, RoundingMode.HALF_UP)`. (Req 6.1–6.4)
- [x] 6.2 `common/AuditInfo.java` — record with `@NotNull Instant createdAt`, `@NotBlank String createdBy`, `@NotNull Instant updatedAt`, `@NotBlank String updatedBy`, `Long version`. (Req 6.5–6.7)
- [x] 6.3 `common/PageRequest.java` — record with `@Min(0) int page`, `@Min(1) @Max(100) int size`, `String sortBy`, `String sortDirection`. (Req 6.8–6.10)
- [x] 6.4 `common/PageResponse.java` — generic record with `@NotNull List<T> content`, `int page`, `int size`, `long totalElements`, `int totalPages`. (Req 6.11–6.12)
- [x] 6.5 Unit tests `MoneyTest`, `AuditInfoTest`, `PageRequestTest`, `PageResponseTest`: valid + validation + round-trip + scale enforcement for `Money`. **Verify:** tests green. (Req 11)

## 7. Serialization config (§5)
- [x] 7.1 `config/DomainObjectMapper.java` — static `create()` factory: `new ObjectMapper()` + `JavaTimeModule` + `WRITE_DATES_AS_TIMESTAMPS=false` + `ZoneIdSerializer`/`ZoneIdDeserializer` registered via `SimpleModule`. (§5, Req 9)
- [x] 7.2 `config/ZoneIdSerializer.java` + `config/ZoneIdDeserializer.java` — write/read IANA zone name string. (§5, Req 9.1)
- [x] 7.3 Update `TestObjectMapperProvider` (task 0.4) to use `DomainObjectMapper.create()`. (§7)
- [x] 7.4 Add serialization round-trip assertion to every `*Test` class using `TestObjectMapperProvider`. (Req 10.1)
- [x] 7.5 `PageResponseTest` — deserialize `PageResponse<TradeRecord>` using `TypeReference`, assert element list equality. (Req 10.4)

## 8. Property-based tests (Req 11.3 / GP-Rq-12.3)
- [x] 8.1 `MoneyArbitraryTest` (jqwik) — property: for any valid `BigDecimal` amount > 0, `Money.amount.scale() == 2`. (§3.2)
- [x] 8.2 `CurrencyPairArbitraryTest` (jqwik) — property: any 3-char uppercase string is accepted for `baseCurrency`/`quoteCurrency`; any 2-char string is rejected. (§4)

## 9. Final verification
- [x] 9.1 `mvn -pl Middleware/shared-domain-contracts verify` — all unit + property tests green, zero failures. (GP-Rq-12.5)
- [x] 9.2 Confirm no Spring Boot, Kafka, JPA, or runtime-framework dependency appears in `pom.xml` compile scope. (Req 1.5)
- [x] 9.3 Update `MASTER-PLAN.md`: mark `01-shared-domain-contracts` design ✅ tasks ✅.

---
**Completion:** 32 / 32 tasks. Update this line as tasks are ticked.
