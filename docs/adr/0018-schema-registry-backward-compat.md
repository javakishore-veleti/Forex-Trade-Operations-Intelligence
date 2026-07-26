# ADR-0018: Schema Registry BACKWARD Compatibility as Default

## Status
Accepted

## Context
Domain events published to Kafka are serialized with Avro (via Confluent Schema Registry). As the
platform evolves, event schemas change — new fields are added, optional fields removed, types
refined. Consumers may be running older code during rolling deployments.

The compatibility mode determines what schema changes are allowed without breaking consumers.
Choosing the wrong mode leads to either production serialization failures or an inability to
evolve schemas without coordinated deployments.

## Decision
Set **BACKWARD compatibility** as the default mode for all subjects in Schema Registry.

```json
{
  "compatibilityLevel": "BACKWARD"
}
```

BACKWARD compatibility means: **new schema can read data written by the old schema**. In practice:
- Adding a field with a default value → allowed
- Removing a field → allowed (new reader ignores it)
- Renaming a field → NOT allowed (breaks deserialization)
- Changing a field type → NOT allowed

This maps to our deployment model: consumers upgrade first, then producers. A new consumer
(with new schema) must read old events still in Kafka (written with old schema).

## Alternatives Considered

| Alternative | Why rejected |
|-------------|-------------|
| **FORWARD compatibility** | Requires producers to upgrade first; in our model consumers upgrade first (canary consumer groups); would require reversing deployment order |
| **FULL compatibility** | Most restrictive — forbids both field additions without defaults AND field removals; slows schema evolution for minimal added safety given our deploy model |
| **NONE** | No compatibility checking; allows any change; high risk of runtime deserialization failures; unacceptable for financial data |
| **BACKWARD_TRANSITIVE** | Checks against ALL previous versions (not just latest); overly strict for a platform where we prune deprecated fields after 2 release cycles |

## Consequences

### Positive
- Consumers can safely upgrade independently — new code reads old events
- Rolling deployments proceed without schema coordination ceremonies
- Schema Registry rejects breaking changes at registration time (fail-fast in CI)
- Compatible with Kafka's event retention — old events remain readable for their full retention period

### Negative
- Producers cannot freely remove fields until all consumers have upgraded past them
- All new fields must have default values (slightly more verbose schema definitions)
- If deployment order is reversed (producer first), temporary serialization warnings may occur

### Mitigations
- CI pipeline validates schema compatibility before merge (Maven plugin: `schema-registry:test-compatibility`)
- Deprecated fields annotated in Avro schema with `@deprecated` doc; removed after 2 release cycles
- Deployment runbooks enforce consumer-first upgrade order for schema-changing releases
- Override to FULL compatibility available per-subject for critical schemas (e.g., `fxops.trade.lifecycle-value`)
