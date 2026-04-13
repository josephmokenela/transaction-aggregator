# ADR-005: Kafka Event Schema Design and Evolution Strategy

**Status:** Accepted  
**Date:** 2026-04-13

## Context

The `transactions` Kafka topic is a cross-service contract. This service both produces
events to it (via `KafkaTransactionProducer`) and consumes them (via
`KafkaTransactionConsumer`). Any other service that reads from this topic — a reporting
service, a fraud detection pipeline, an audit log consumer — becomes a downstream
dependent on the schema of `KafkaTransactionEvent`.

Unlike a REST API, a Kafka topic has no single owner of the consumer connection. Producers
and consumers are decoupled in time: a consumer may process a message hours or days after
it was published. This means schema changes to the event cannot be coordinated with a
single deployment — there will be a window where old and new messages coexist on the topic,
and potentially a window where old and new consumers run simultaneously.

The current `KafkaTransactionEvent` schema is:

```
id, customerId, accountId, amount, currencyCode,
type (CREDIT|DEBIT), status (COMPLETED|PENDING|FAILED),
description, merchantName, occurredAt
```

All fields are serialised as JSON. Enum values (`type`, `status`) are stored as their
string names.

## Decision

### 1. JSON with explicit field naming — no schema registry for now

Serialise events as JSON. Do not introduce a schema registry (Confluent Schema Registry,
AWS Glue) at this stage.

A schema registry adds operational dependencies (another service to deploy, secure, and
monitor) and is most valuable when there are many independent producer and consumer teams
that need a central source of truth for schema versions. For a single-team service with a
small number of consumers, the overhead is not justified yet.

The decision is revisited in the "Evolution path" section below.

### 2. Additive-only changes — forward and backward compatibility

All changes to `KafkaTransactionEvent` must be **additive**:

- **Adding a new optional field** is safe. Existing consumers that don't know about the
  field will ignore it (Jackson ignores unknown fields by default).
- **Removing a field** is a breaking change. Consumers that read the field will either
  fail or silently receive `null`. Removal requires a coordinated, multi-phase migration
  (see below).
- **Renaming a field** is equivalent to removing the old name and adding a new one —
  treat it as a breaking change.
- **Changing a field's type** (e.g. `String` → `UUID`) is a breaking change even if the
  semantic value is the same.
- **Adding a new enum value** (e.g. a new `status`) must be handled gracefully by
  consumers. Any consumer that uses `switch` or `valueOf` without a default will throw.
  New values must be documented in this ADR before they are produced.

### 3. Consumer tolerance — unknown fields must not cause failures

All consumers of this topic must configure their deserialiser to **ignore unknown
properties**. In Jackson this is the default, but it must be verified for any new
consumer:

```java
objectMapper.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
```

This is what makes additive changes safe. Without this, adding any field is a breaking
change for that consumer.

### 4. Breaking changes follow a three-phase migration

When a breaking change is unavoidable (field removal, rename, type change):

**Phase 1 — Dual-write:** Produce both the old and new field in parallel. All existing
consumers continue reading the old field.

```json
{ "merchantName": "Costa Coffee", "merchant": { "name": "Costa Coffee", "id": "mc-123" } }
```

**Phase 2 — Consumer migration:** Migrate all known consumers to read the new field.
Agree a cutover date with consumer teams.

**Phase 3 — Old field removal:** Once all consumers have deployed, stop producing the
old field. This is now a safe additive-only change from the consumers' perspective.

Each phase requires a separate deployment. The timeline between phases depends on how
quickly consumers can be updated — plan for at least one sprint per phase.

### 5. Enum values are additive, not removable

Current values:

| Field    | Values                          |
|----------|---------------------------------|
| `type`   | `CREDIT`, `DEBIT`               |
| `status` | `COMPLETED`, `PENDING`, `FAILED`|

Adding a new value (e.g. `REVERSAL` to `type`) is additive and safe only if all consumers
handle unknown enum values gracefully — typically by mapping to an `UNKNOWN` fallback
rather than calling `valueOf()` directly.

Removing an existing enum value follows the same three-phase migration as field removal.

### 6. Message key is the transaction ID

The Kafka message key is set to `event.id()` (the transaction UUID). This ensures that
all events for the same transaction are routed to the same partition, preserving ordering
for retry and idempotency scenarios. Consumers must not rely on global topic ordering —
only partition-level ordering is guaranteed.

## When to introduce a schema registry

Introduce Confluent Schema Registry (or equivalent) when any of these conditions are met:

- More than two independent consumer teams read the topic
- A consumer team deploys on a different release cadence (e.g. a mobile team that
  releases monthly)
- The schema needs to be validated before a producer can publish (preventing bad events
  from entering the topic at all)
- Audit requirements demand a versioned, immutable record of every schema that has
  ever been in production

At that point, migrate to Avro or Protobuf serialisation. JSON with a registry is
possible but loses the main benefit (binary compactness and strict schema enforcement).

## Consequences

- New consumers onboarding to the `transactions` topic must read this ADR and configure
  their deserialiser for unknown-field tolerance before going live.
- Any engineer adding a field to `KafkaTransactionEvent` must update the enum table
  above and get explicit sign-off that downstream consumers are not affected.
- Breaking changes require cross-team coordination — they cannot be delivered in a single
  PR without the three-phase migration plan being agreed first.
- The absence of a schema registry means there is no automated enforcement of these rules.
  Code review and this document are the only gates.

## Alternatives considered

**Avro with schema registry from day one:** Provides compile-time schema validation and
automatic compatibility checks. Rejected because the operational overhead (running a
schema registry, managing Avro code generation in the build) is disproportionate to the
current number of consumers. The JSON + additive-only discipline achieves the same safety
properties with less infrastructure.

**Protobuf:** Similar trade-offs to Avro. Binary encoding is more efficient than JSON at
scale, but the current message volume does not justify the toolchain complexity.
