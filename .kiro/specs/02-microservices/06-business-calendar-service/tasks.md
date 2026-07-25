# Tasks — Business Calendar (Bounded Context)

> **Stage 3 of 3** (`requirements.md → design.md → tasks.md`). Derived from `design.md`. Execute
> top-to-bottom; each task is atomic, maps to specific files, and is independently verifiable.
> This is a **living checklist** — mark `[x]` as each task is completed and verified. Tags in
> parentheses trace to design sections (§) and requirements (Req / GP-Rq).

## 0. Module scaffold
- [ ] 0.1 Create Maven module `Middleware/business-calendar-service` with `<parent>` → `Middleware/pom.xml`; add to parent `<modules>`. (§2)
- [ ] 0.2 Add dependencies: `shared-domain-contracts`, Spring Web, Spring Data JPA + PostgreSQL driver, Jakarta Bean Validation, Actuator, Micrometer/OTel, Testcontainers (test), jqwik (test). *(products resolved from technology-stack)* (§1)
- [ ] 0.3 `BusinessCalendarApplication` (`@SpringBootApplication`) + `application.yml` (`spring.application.name=business-calendar-service`). (§2)
- [ ] 0.4 Context-load test asserting the application context starts. **Verify:** `mvn -pl Middleware/business-calendar-service test` green.

## 1. Domain — regional calendar model (Req 1, 2)
- [ ] 1.1 `domain/RegionCalendar`, `domain/Holiday`, `domain/Cutoff`, `domain/BusinessDayReason` (`WEEKEND`/`HOLIDAY`/`BUSINESS_DAY`), `domain/BookingDate`, `domain/GlobalBusinessDate`. (§3, §5, §6)
- [ ] 1.2 `domain/CalendarRegistry` (immutable in-memory authoritative view): `calendarFor(RegionCode)`; `GLOBAL` anchored to base-country zone `America/New_York`. (§3, Req 1.2)
- [ ] 1.3 Default weekend `{SATURDAY,SUNDAY}` with per-region override; region-scoped holiday sets. (§3, Req 1.4, 2.3)
- [ ] 1.4 `UnknownRegionCalendarException` for missing/invalid `RegionCode`. (§3, Req 2.5, 4.4)

## 2. Persistence — relational reference data (Req 1.3; GP-Rq-6)
- [ ] 2.1 `persistence/relational` entities `RegionCalendarEntity` (with `@Version`), `HolidayEntity`, `CutoffEntity` (fields per §7 tables). (GP-Rq-6)
- [ ] 2.2 Spring Data JPA repositories for the three entities. (§7)
- [ ] 2.3 Schema migration for `region_calendar`, `holiday` (unique `(region, holiday_date)`), `cutoff` (`cutoff_local_time` as local `time`, never a UTC offset). **Verify:** migration applies on a Testcontainers Postgres. (§7, Req 3.5)
- [ ] 2.4 `config/CalendarBootstrapConfig` loads reference data on startup, validates each IANA zone via `ZoneId.of(...)`, builds `CalendarRegistry`. (§11, Req 1.3)

## 3. Domain — DST-aware time handling (Req 3)
- [ ] 3.1 `domain/calc/DstResolver` resolving local times with `ZonedDateTime`/`ZoneId` using the offset in effect at the instant (never fixed). (§4, Req 3.1/3.2/3.5)
- [ ] 3.2 Documented deterministic rules: spring-forward gap ⇒ push forward to first valid instant; fall-back overlap ⇒ `withEarlierOffsetAtOverlap()`. (§4, Req 3.3/3.4)

## 4. Domain — business-day classification & arithmetic (Req 4)
- [ ] 4.1 `domain/calc/BusinessDayCalculator.classify(region,date) -> BusinessDayReason` + `isBusinessDay`. (§5, Req 4.1)
- [ ] 4.2 `addBusinessDays(region,date,n)` skipping weekends/holidays (negative `n` backward, `n=0` unchanged). (§5, Req 4.2)
- [ ] 4.3 `businessDaysBetween(region,from,to)` over half-open `[from,to)`. (§5, Req 4.3)
- [ ] 4.4 Unit tests: weekend/holiday/business-day across ≥2 regions with different IANA zones; add/between edge cases. **Verify:** unit tests green. (Req 6.1)

## 5. Domain — cutoff & booking-date classification (Req 5)
- [ ] 5.1 `domain/calc/BookingDateCalculator.bookingDate(region,instant)`: at-or-before cutoff on a business day ⇒ that date; else next business day. (§6, Req 5.2/5.3/5.4)
- [ ] 5.2 `isPostCutoff(region,instant)` for the current business date's cutoff. (§6, Req 5.5)
- [ ] 5.3 `domain/calc/GlobalBusinessDateCalculator.globalBusinessDate(instant)` anchored to base-country zone. (§6, Req 5.6, 1.2)

## 6. API — read models (Req 2.4, 4, 5; GP-Rq-1)
- [ ] 6.1 `application/CalendarQueryService` orchestrating registry + calculators. (§8, §10)
- [ ] 6.2 `api/CalendarQueryController` `/api/v1/calendars/**` endpoints + DTOs (business-day, add-business-days, business-days-between, holidays, booking-date, post-cutoff, global business-date); read-only. (§8)
- [ ] 6.3 `RegionCode`/param validation (`BEAN_VALIDATION`): invalid region → 400, unknown calendar → 404. (§8, §12, Req 2.5, 4.4)

## 7. Golden-path realizations (inherited NFRs → concrete) (§9)
- [ ] 7.1 `web/CorrelationIdFilter` (adopt/generate `X-Correlation-Id`, MDC copy, response echo) + `%X{correlationId}` log pattern. (GP-Rq-2)
- [ ] 7.2 `web/GlobalExceptionHandler` (`@RestControllerAdvice`) → 400/404/500 envelopes, no stack traces in body. (GP-Rq-3)
- [ ] 7.3 `health/ReadinessHealthIndicator`: `UP` only when Postgres reachable **and** `CalendarRegistry` loaded. (GP-Rq-4)
- [ ] 7.4 `config/SecurityConfig` permit-all + standard Phase-6 auth TODO marker. (GP-Rq-9)
- [ ] 7.5 Business metrics `calendar_booking_date_total{region}`, `calendar_business_day_total{region,reason}` via Micrometer. (GP-Rq-8)

## 8. Tests — domain scenarios (Req 6; GP-Rq-12)
- [ ] 8.1 Web-layer tests (MockMvc): each endpoint success + 400 (invalid region) + 404 (unknown calendar). (GP-Rq-12.1)
- [ ] 8.2 DST edge-case tests: spring-forward gap + fall-back overlap for ≥1 region resolve per the documented rule. (Req 6.3)
- [ ] 8.3 Property tests (jqwik): `addBusinessDays` yields a business day; round-trip `+n/-n`; `businessDaysBetween >= 0`; determinism — identical inputs + reference-data version ⇒ identical answers. (Req 6.4, GP-Rq-13)
- [ ] 8.4 Integration (Testcontainers Postgres): load reference data → registry populated → booking-date/business-day queries end-to-end. (Req 6.2)
- [ ] 8.5 All fixtures use fictional holidays, standard IANA zones, synthetic `FX-` ids. (Req 6.5, GP-Rq-14)

## 9. Verification & tracking
- [ ] 9.1 `mvn -pl Middleware/business-calendar-service verify` — build + all tests green.
- [ ] 9.2 Update `MASTER-PLAN.md`: mark `06-business-calendar-service` design+tasks+code complete.
- [ ] 9.3 Commit via `scripts/commit-specs.sh` / normal commit.

---
**Completion:** 0 / 37 tasks. Update this line as tasks are ticked.
