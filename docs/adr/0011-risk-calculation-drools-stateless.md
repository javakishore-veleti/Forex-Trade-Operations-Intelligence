# ADR-0011: Drools StatelessKieSession per Risk Calculation

## Status
Accepted

## Context
The `risk-calculation-service` evaluates trade risk using 200+ business rules that vary by:
- Product type (spot, forward, swap, NDF)
- Currency pair volatility tier
- Counterparty credit rating
- Settlement window and netting eligibility

These rules are authored by risk analysts (not developers), change monthly, and interact in complex
ways (rule chaining, salience ordering). The calculation for a single trade like `FX-000042` may
fire 15–30 rules in sequence to produce a final `RiskScore`.

## Decision
Use **Drools 9 with a StatelessKieSession created per calculation request**.

```java
@Service
public class RiskCalculationEngine {
    private final KieBase kieBase; // loaded once at startup from .drl files

    public RiskResult calculate(TradeRiskFacts facts) {
        StatelessKieSession session = kieBase.newStatelessKieSession();
        session.execute(CommandFactory.newInsertElements(facts.asList()));
        return facts.extractResult();
    }
}
```

Key design choices:
- **Stateless session**: created, used, discarded — no shared mutable state between requests
- **KieBase** compiled once at startup (expensive) and reused (thread-safe)
- `.drl` files stored in `src/main/resources/rules/` — versioned with the service
- Rule output written into a `RiskResult` fact inserted into working memory

## Alternatives Considered

| Alternative | Why rejected |
|-------------|-------------|
| **Stateful KieSession (shared)** | Requires careful session lifecycle management; risk of fact leakage between trades; concurrency hazards |
| **Embedded DSL (Java fluent API)** | Loses analyst-authoring capability; rules become code requiring developer PRs for every change |
| **GraalVM polyglot (JS/Python rules)** | Adds polyglot runtime complexity; no mature tooling for rule authoring/testing comparable to Drools Workbench |
| **Hard-coded if/else chains** | Unmaintainable at 200+ rules; no conflict resolution; no rule tracing for audit |

## Consequences

### Positive
- Each calculation is isolated — no session contamination between `FX-000042` and `FX-000043`
- Rules are human-readable DRL — risk analysts can review and propose changes
- Drools audit log provides full rule-firing trace for regulatory explainability
- KieBase reuse means session creation cost is negligible (~0.1ms)

### Negative
- Drools adds a significant dependency (20+ JARs) to the service
- DRL syntax has a learning curve for new developers
- Rule interactions (salience, activation-group) can be subtle to debug
- No hot-reload without restart (acceptable for monthly rule changes via deployment)

### Mitigations
- Comprehensive rule unit tests using `@DroolsSession` test harness (95+ tests)
- Rule-firing audit log emitted as a structured event for every calculation
- DRL files linted in CI with a custom Maven plugin that checks for common anti-patterns
