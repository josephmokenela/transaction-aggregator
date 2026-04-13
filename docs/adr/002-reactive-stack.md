# ADR-002: Reactive Stack (WebFlux + R2DBC)

**Status:** Accepted  
**Date:** 2026-04-12

## Context

The service does a lot of I/O: it reads from PostgreSQL, calls external data sources (Plaid API), produces and consumes Kafka events, and serves concurrent HTTP requests. With a traditional blocking stack (Spring MVC + JPA), each in-flight request holds a thread for the duration of its I/O operations. Under high concurrency this leads to thread exhaustion before CPU or network become the bottleneck.

## Decision

Use Spring WebFlux (Netty event loop) for the HTTP layer and R2DBC for non-blocking database access. All service operations return `Mono` or `Flux` — no blocking calls anywhere in the request path.

## Consequences

**Benefits:**
- Event-loop model handles many concurrent connections with a small, fixed thread pool — better resource utilisation under I/O-heavy load
- Backpressure propagates end-to-end: from HTTP through to the database, preventing unbounded memory consumption under load spikes
- Reactive Kafka consumer (`ReactiveKafkaConsumerTemplate`) integrates naturally with the rest of the pipeline
- Spring Security's reactive support (`ServerHttpSecurity`) composes cleanly with the WebFlux filter chain

**Trade-offs:**
- Reactive programming requires a different mental model — debugging stack traces are harder to read, and blocking calls (JDBC, synchronous HTTP clients) inside a reactive pipeline are a silent performance killer
- R2DBC is less mature than JPA: no lazy loading, limited query DSL, no automatic dirty checking
- Flyway requires a blocking JDBC `DataSource` for migrations — a separate `DataSource` bean is required alongside R2DBC purely for Flyway at startup

## Constraint enforced

No blocking calls in the reactive pipeline. Flyway migration is the only intentionally blocking operation and runs on a dedicated thread before the reactive context starts.

## Alternatives considered

**Spring MVC + JPA:** Simpler to write and debug, wider ecosystem support. Appropriate for most CRUD services. Rejected here because the concurrent I/O profile (multiple external data sources, Kafka, DB) benefits materially from non-blocking I/O.

**Virtual threads (Project Loom):** Available in Java 21+. Would allow a blocking style with similar concurrency characteristics. Not chosen because the project was designed for reactive from the outset and the reactive security/Kafka integration is more established.
