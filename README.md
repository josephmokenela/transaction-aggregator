# Transaction Aggregator

A reactive REST API that aggregates customer financial transaction data from multiple data sources, automatically categorises transactions, and exposes rich querying and aggregation endpoints.

## Architecture

The project follows **hexagonal architecture** (ports & adapters):

```
domain/          ← pure business logic; no framework dependencies
  model/         ← immutable value objects (Java records): Money, Transaction, …
  port/in/       ← use-case interfaces (what the application can do)
  port/out/      ← output port interfaces (what the application needs)
  exception/     ← domain exceptions

application/
  service/       ← use-case implementations; orchestrate domain + ports

adapter/
  in/web/        ← Spring WebFlux controllers + DTOs
  out/persistence/ ← R2DBC entities, repositories, persistence adapters
  out/datasource/  ← mock data source adapters (bank, card, payment provider)

config/          ← OpenAPI bean
```

## Prerequisites

| Tool | Version |
|------|---------|
| Java | 25 |
| Maven | 3.9+ |
| Docker + Docker Compose | 24+ |

## Running locally (Docker Compose — recommended)

```bash
# Build the image and start postgres + app
docker compose up --build

# API is available at http://localhost:8080
# Swagger UI: http://localhost:8080/swagger-ui.html
# Actuator:   http://localhost:8080/actuator/health
# Prometheus: http://localhost:8080/actuator/prometheus
```

Stop with `docker compose down` (add `-v` to also remove the database volume).

## Running locally without Docker

Start a PostgreSQL instance (e.g. via Docker):

```bash
docker run -d \
  --name tx-db \
  -e POSTGRES_DB=transaction_aggregator \
  -e POSTGRES_USER=postgres \
  -e POSTGRES_PASSWORD=postgres \
  -p 5432:5432 \
  postgres:17-alpine
```

Then run the application (Flyway migrations execute automatically on startup):

```bash
./mvnw spring-boot:run
```

Or build and run the JAR:

```bash
./mvnw package -DskipTests
java -jar target/transaction-aggregator-*.jar
```

## Building the Docker image standalone

```bash
./mvnw package -DskipTests
docker build -t transaction-aggregator:latest .
```

## Running tests

```bash
# Unit tests only (no Docker required)
./mvnw test -Dgroups='!integration'

# All tests including integration tests (requires Docker for Testcontainers)
./mvnw verify
```

The integration tests (`*IT.java`) use **Testcontainers** to spin up a real PostgreSQL instance automatically — no manual setup needed.

## API overview

All endpoints are documented interactively at `/swagger-ui.html`.

### Transactions — `POST /api/v1/transactions`

Record a manual transaction. The category is assigned automatically.

```json
{
  "customerId": "11111111-1111-1111-1111-111111111111",
  "accountId": "aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa",
  "amount": 3500.00,
  "currency": "GBP",
  "type": "CREDIT",
  "description": "Monthly salary payment",
  "merchantName": "Employer Ltd"
}
```

### Transactions — `GET /api/v1/transactions`

Search with optional filters: `customerId`, `accountId`, `category`, `type`, `dataSourceId`, `keyword`, `from`, `to`, `limit`.

### Transactions — `GET /api/v1/transactions/aggregate`

Aggregate a time window into HOURLY / DAILY / WEEKLY / MONTHLY / YEARLY buckets.

```
GET /api/v1/transactions/aggregate
  ?accountId=aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa
  &period=MONTHLY
  &from=2024-01-01T00:00:00Z
  &to=2024-03-31T23:59:59Z
```

### Sync — `POST /api/v1/sync`

Pull transactions from all registered mock data sources for a customer over a date range. Idempotent.

```json
{
  "customerId": "11111111-1111-1111-1111-111111111111",
  "from": "2024-01-01T00:00:00Z",
  "to": "2024-01-31T23:59:59Z"
}
```

### Customers — `GET /api/v1/customers/{id}/summary`

Full financial summary — total inflow/outflow, net position, category breakdown.

### Customers — `GET /api/v1/customers/{id}/categories`

Per-category spend totals with percentage of overall activity.

## Observability

| Endpoint | Description |
|----------|-------------|
| `GET /actuator/health` | Liveness + readiness (DB connectivity included) |
| `GET /actuator/prometheus` | Prometheus-format metrics |
| `GET /actuator/metrics` | Metrics index |

Key custom metrics:

| Metric | Description |
|--------|-------------|
| `transactions.recorded` | Counter — manual transactions recorded (tagged by `type`, `category`) |
| `transactions.synced` | Counter — transactions ingested via sync |
| `sync.errors` | Counter — sync failures (tagged by `source`) |

## Seeded demo data

Flyway migration `V2` seeds three demo customers:

| ID | Name |
|----|------|
| `11111111-1111-1111-1111-111111111111` | Alice Johnson |
| `22222222-2222-2222-2222-222222222222` | Bob Smith |
| `33333333-3333-3333-3333-333333333333` | Carol Williams |

Use the sync endpoint to populate their transactions from the mock data sources.

## Configuration

Key environment variables (all have sensible defaults for local dev):

| Variable | Default | Description |
|----------|---------|-------------|
| `DB_HOST` | `localhost` | PostgreSQL host |
| `DB_PORT` | `5432` | PostgreSQL port |
| `DB_NAME` | `transaction_aggregator` | Database name |
| `DB_USERNAME` | `postgres` | Database user |
| `DB_PASSWORD` | `postgres` | Database password |
| `SPRING_PROFILES_ACTIVE` | _(none)_ | Set to `docker` inside containers |
