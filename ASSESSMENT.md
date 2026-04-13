# Principal Engineer Level Assessment

This document tracks the project's progress towards Principal Engineer level. It is reviewed and updated as the project evolves.

**Last reviewed:** 2026-04-12

---

## Summary

| Level | Status |
|-------|--------|
| Mid-level | Clearly past |
| Senior Engineer | Clearly past |
| Staff Engineer | Clearly past |
| **Principal Engineer** | **Getting close** — implementation quality and rigour are now aligned; remaining gaps are data-layer infrastructure concerns |

---

## Strengths

### Architecture
- Hexagonal architecture (ports & adapters) correctly applied end-to-end
- Domain is clean with no framework leakage
- Use-case interfaces (ports/in) are well-defined and respected throughout
- Immutable value objects (Java records) throughout the domain model

### Reactive Stack
- WebFlux, R2DBC, reactive Kafka consumer, and reactive Spring Security all applied correctly end-to-end
- No blocking calls mixed into the reactive pipeline — a common mistake avoided

### Security
- JWT issuance with HMAC-SHA256, role-based access control (`ROLE_CUSTOMER`, `ROLE_ADMIN`)
- Ownership enforcement at the controller layer on every endpoint
- Rate limiting on the auth endpoint (Resilience4j)
- Vault integration for secrets in docker/k8s profiles
- Correct JSON error responses for 401/403 (no Spring whitelabel pages)

### Observability
- Prometheus metrics with custom counters (tagged by `type`, `category`, `source`)
- Distributed tracing with Zipkin
- Spring Actuator with health, metrics, and prometheus endpoints
- All three observability pillars (metrics, tracing, logging) present

### Resilience
- Resilience4j rate limiting and circuit breaker wired in and actively used — not just added as a dependency

---

## Gaps to Close

### 1. Testing — _highest priority_

**Current state:** 6 test files, zero controller/HTTP layer tests, zero auth flow tests.

**What's needed:**
- Controller tests covering the full HTTP layer (request/response, status codes, validation errors)
- Auth flow tests — verify customers cannot access other customers' data, verify 401 on missing token, 403 on wrong role
- Kafka consumer tests covering batch processing, retry logic, and DLT routing
- Resilience4j tests verifying rate limits actually block at the configured thresholds

At Principal level the test suite tells a complete story: unit tests for domain logic, integration tests for the persistence layer, API-level tests for the HTTP contract.

### 2. Architecture Decision Records (ADRs)

**Current state:** `ARCHITECTURE.md` describes the structure but does not capture decisions.

**What's needed:** ADRs for key choices so the team doesn't re-litigate them:
- Why reactive (WebFlux/R2DBC) over servlet/JPA
- Why HMAC-SHA256 (HS256) over RSA (RS256) for JWT signing
- Why self-issued tokens over an external IdP
- Why hexagonal over layered architecture

### 3. CI/CD — incomplete

**Current state:** GitHub Actions pipeline builds and pushes the image but the deployment step is commented out. Deployments are manual.

**What's needed:**
- Fully wired pipeline — build, test, image push, and deploy all automated
- Deployment validation (kubectl dry-run or equivalent)
- Clear rollback strategy

### 4. Kubernetes — operational hardening

**Current state:** Core manifests exist (deployment, service, configmap) but the setup is not production-hardened.

**What's needed:**
- Ingress with TLS
- NetworkPolicy (deny-all baseline + explicit allow rules)
- HorizontalPodAutoscaler
- Postgres credentials in a Secret resource (currently hardcoded in the StatefulSet)
- Resource requests and limits on all containers

### 5. Operational concerns

- `DataSyncService` parallel data source fetches have no timeout — a hanging source blocks indefinitely
- No request correlation ID / MDC propagation alongside the Zipkin tracing
- Kafka consumer lag has no alerting threshold defined

---

## Progress Log

| Date | Update |
|------|--------|
| 2026-04-12 | Initial assessment written. Fixed aggregate endpoint security hole (missing customer scoping). Fixed `ErrorResponse.of()` visibility bug. |
| 2026-04-12 | **Addressed all Staff Engineer gaps:** controller integration tests (Auth, Transaction, Customer — 20 tests covering auth flows and ownership enforcement); `AbstractWebIntegrationTest` base class with real JWT token helpers; DataSyncService 30-second timeout on source fetches; Postgres credentials moved to a Kubernetes Secret; Service changed from NodePort to ClusterIP; Ingress manifest with TLS and nginx annotations; NetworkPolicy (deny-all baseline + explicit allow rules for all ingress/egress paths); HPA (2–10 replicas, CPU 70% / memory 80% targets); CI/CD manifest validation step + conditional deploy on `KUBECONFIG` secret; ADRs for hexagonal architecture, reactive stack, JWT signing strategy, and self-issued tokens. |
| 2026-04-12 | **Addressed Principal Engineer gaps (1–3):** Kafka consumer tests (9 tests — batch, DLT routing, retry logic, mixed batch, Micrometer counters); DataSyncService tests (12 tests — multi-source aggregation, error isolation, category application, metric counters); MDC correlation ID — `CorrelationIdFilter` + `RequestIdMdcAccessor` propagate `X-Request-ID` through the Reactor context to MDC on every operator boundary, echoed in response headers, visible in all log lines; PII masking — `Mask` utility class, explicit masking at 2 DEBUG call sites, safe `toString()` on `Transaction`, `RecordTransactionCommand`, `KafkaTransactionEvent`, `TransactionFilter`. Unit test count: 86 (unit) + 23 (integration) = 109 total. |
| 2026-04-12 | **Closed Kafka consumer lag alerting gap:** 3 Prometheus alert rules added (`KafkaConsumerLagHigh` warning at >1,000 records for 5m; `KafkaConsumerLagCritical` critical at >5,000 for 2m; `KafkaConsumerLagGrowing` warning when `deriv()` is positive for 10m — catches stalled consumer before lag crosses an absolute threshold). Rules use `kafka_consumer_fetch_manager_records_lag_max` emitted by Spring Boot's Micrometer Kafka integration through the existing `/actuator/prometheus` scrape target. Applied to both `monitoring/alerts.yml` (Docker Compose) and `k8s/monitoring/prometheus/alerts-configmap.yaml` (Kubernetes). |
| 2026-04-13 | **Closed Postgres backup gap:** `k8s/postgres/backup-cronjob.yaml` — daily `pg_dump` at 02:00 UTC using custom format (compress=9), 7-day local retention via `find -mtime +7 -delete`, 10-minute deadline, `backoffLimit: 1`, stored on a dedicated 5 Gi PVC (`backup-pvc.yaml`). Uses existing `postgres-credentials` Secret. Inline comments document the path to object-storage offload (S3/GCS via rclone) and PITR (pgWAL-G) for production hardening. Alert recipe included for detecting missed backup windows. |

---

## Revised Assessment

| Level | Status |
|-------|--------|
| Mid-level | Clearly past |
| Senior Engineer | Clearly past |
| Staff Engineer | Clearly past |
| **Principal Engineer** | **Getting close** — test depth and operational rigour now consistent; one remaining gap is a platform-level infrastructure decision |

---

## Remaining gaps to Principal Engineer

1. **No readonly replica** — all reads go to the single Postgres instance; read replicas or caching layer not considered. This is a platform/DBA decision (streaming replication, PgBouncer, or a read-through cache) that sits outside application code — appropriate to defer to the platform team in a real production context.
