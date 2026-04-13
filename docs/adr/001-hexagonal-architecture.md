# ADR-001: Hexagonal Architecture (Ports & Adapters)

**Status:** Accepted  
**Date:** 2026-04-12

## Context

The service aggregates transactions from multiple external sources (Plaid, mock bank/card/payment adapters), exposes them via a REST API, and publishes/consumes Kafka events. Without a clear structural boundary, business logic tends to accumulate in controllers or repositories, becoming tightly coupled to framework details and difficult to test in isolation.

## Decision

Adopt hexagonal architecture. The domain package contains pure business logic with no framework imports. Use-case interfaces (ports/in) define what the application can do. Output port interfaces (ports/out) define what the application needs from infrastructure. Adapters implement these ports — web controllers drive the application in; persistence and data source adapters drive it out.

## Consequences

**Benefits:**
- Domain logic is testable with plain JUnit — no Spring context, no database, no mocks of framework types
- Swapping infrastructure is mechanical: replace an adapter without touching the domain (e.g. swap R2DBC for MongoDB by reimplementing `LoadTransactionPort`)
- The forced separation makes dependencies explicit and unidirectional — domain never imports adapter code
- New data sources (real Plaid, Open Banking) are added by implementing `FetchTransactionsPort` — zero changes to business logic

**Trade-offs:**
- More files and indirection than a layered (controller → service → repository) structure
- Onboarding cost: developers unfamiliar with hexagonal architecture need orientation before they are productive
- The discipline must be enforced in code review — nothing in the compiler prevents an adapter from importing domain internals in the wrong direction

## Alternatives considered

**Layered architecture (controller → service → repository):** Simpler to understand initially but leads to business logic leaking into the repository layer and makes unit testing the service difficult without a database.
