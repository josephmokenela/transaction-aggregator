# Architecture

## System Overview

End-to-end view of all actors, application components, and infrastructure services.

```mermaid
flowchart TB
    subgraph Actors["External Actors"]
        CUSTOMER["Customer\n(end user)"]
        ADMIN["Administrator\n(ops / tooling)"]
    end

    subgraph APP["Transaction Aggregator  ·  Spring Boot 3  ·  WebFlux / Netty  ·  Java 25"]
        subgraph SEC["Security  ·  Spring Security + HMAC-SHA256 JWT"]
            AUTH_C["AuthController\nPOST /auth/token\nPOST /auth/admin-token\nrate-limited · 10 req/min"]
            JWT_FILTER["JWT Filter\nROLE_CUSTOMER  ·  ROLE_ADMIN"]
        end

        subgraph WEB["REST API  ·  :8080"]
            CUST_C["CustomerController\nGET /customers\nGET /customers/{id}/summary\nGET /customers/{id}/categories\nGET /customers/{id}/transactions"]
            TX_C["TransactionController\nPOST /transactions\nGET  /transactions\nGET  /transactions/aggregate"]
            SYNC_C["DataSyncController\nPOST /sync"]
            KAFKA_C["KafkaGenerateController\nPOST /kafka/generate"]
        end

        subgraph CORE["Application Core  ·  Hexagonal (Ports & Adapters)"]
            TX_SVC["TransactionAggregationService\nrecord · get · search · aggregate"]
            CUST_SVC["CustomerQueryService\nlist · summary · category breakdown"]
            SYNC_SVC["DataSyncService\nfetch → categorise → persist"]
            CAT_SVC["TransactionCategorizationService\nrule-based keyword classification"]
        end

        subgraph PERSIST["Outbound Adapters  ·  Persistence  (R2DBC + circuit breaker)"]
            TX_P["TransactionPersistenceAdapter\ndynamic filters · full-text search\nGROUP BY aggregation · upsert"]
            CUST_P["CustomerPersistenceAdapter\npaginated list"]
        end

        subgraph SOURCES["Outbound Adapters  ·  Data Sources"]
            MOCK["Mock Bank  ·  Mock Card\nMock Payment Provider"]
            PLAID["PlaidDataSourceAdapter\ncircuit breaker"]
        end

        subgraph KAFKA_LAYER["Messaging  ·  Apache Kafka"]
            K_PROD["KafkaTransactionProducer\nDead-Letter Topic on failure"]
            K_CONS["KafkaTransactionConsumer\nbatch listener"]
        end
    end

    subgraph INFRA["Infrastructure"]
        PG[("PostgreSQL 17\nFlyway migrations\nR2DBC connection pool")]
        KAFKA_BROKER[["Apache Kafka\ntransactions topic · DLT"]]
        VAULT(["HashiCorp Vault\nDB password · JWT secrets\nPlaid credentials"])
        ZIPKIN["Zipkin  :9411\ndistributed tracing"]
        PROM["Prometheus  :9090\nmetrics"]
        GRAFANA["Grafana  :3000\ndashboards"]
    end

    CUSTOMER -->|"POST /auth/token"| AUTH_C
    ADMIN -->|"POST /auth/admin-token"| AUTH_C
    CUSTOMER & ADMIN -->|"Bearer JWT"| JWT_FILTER

    JWT_FILTER --> CUST_C & TX_C & SYNC_C & KAFKA_C

    CUST_C --> CUST_SVC
    TX_C   --> TX_SVC
    SYNC_C --> SYNC_SVC
    KAFKA_C --> K_PROD
    K_CONS --> TX_SVC

    TX_SVC --> CAT_SVC
    TX_SVC & CUST_SVC & SYNC_SVC --> TX_P & CUST_P

    SYNC_SVC --> MOCK & PLAID
    MOCK & PLAID -->|"fetched txns → save"| TX_P
    TX_SVC -->|"synthetic data"| K_PROD

    TX_P & CUST_P --> PG
    K_PROD --> KAFKA_BROKER --> K_CONS

    VAULT -.->|"secrets injected at startup"| APP
    APP   -.->|"spans"| ZIPKIN
    PROM  -.->|"scrape /actuator/prometheus"| APP
    GRAFANA --> PROM
```

## Hexagonal Architecture Detail

The application follows a **Hexagonal Architecture** (Ports & Adapters) pattern built on Spring WebFlux (reactive) with R2DBC for non-blocking database access.

```mermaid
graph LR
    subgraph Clients["Clients"]
        HTTP["HTTP Client\n(curl / Swagger UI)"]
        KafkaBroker[["Apache Kafka\n(bitnami/kafka KRaft)"]]
    end

    subgraph PrimaryAdapters["Primary Adapters  ·  adapter/in"]
        CC["CustomerController\n/api/v1/customers"]
        DSC["DataSyncController\n/api/v1/sync"]
        TC["TransactionController\n/api/v1/transactions"]
        KGC["KafkaGenerateController\n/api/v1/kafka/generate"]
        KConsumer["KafkaTransactionConsumer\n@KafkaListener · batch"]
    end

    subgraph Core["Application Core"]
        subgraph UseCases["Services"]
            CQS["CustomerQueryService"]
            DSS["DataSyncService"]
            TAS["TransactionAggregationService"]
            TCS["TransactionCategorizationService"]
        end
        subgraph PortsIn["Input Ports"]
            PIn["ListCustomers\nGetCustomerSummary\nGetCategorySummary\nSearchTransactions\nSyncTransactions\nRecordTransaction\nAggregateTransactions"]
        end
        subgraph PortsOut["Output Ports"]
            POut["SaveTransactionPort\nLoadTransactionPort\nLoadCustomerPort\nFetchTransactionsPort"]
        end
    end

    subgraph SecondaryAdapters["Secondary Adapters  ·  adapter/out"]
        TPA["TransactionPersistenceAdapter\nR2DBC upsert"]
        CPA["CustomerPersistenceAdapter\nR2DBC"]
        MockBank["MockBankDataSourceAdapter\n(salary, bills, rent)"]
        MockCard["MockCardDataSourceAdapter\n(daily spend)"]
        MockPay["MockPaymentProviderAdapter\n(transfers)"]
        KProducer["KafkaTransactionProducer\nKafkaTemplate · synthetic data"]
    end

    subgraph Infrastructure["Infrastructure"]
        PG[("PostgreSQL 17\n(R2DBC + Flyway)")]
        KafkaInfra[["Apache Kafka\ntransactions topic\n6 partitions"]]
    end

    HTTP --> CC & DSC & TC & KGC
    KafkaBroker --> KConsumer

    CC --> PIn
    DSC --> PIn
    TC --> PIn
    KGC --> KProducer

    PIn --> CQS & DSS & TAS
    CQS & DSS & TAS --> TCS
    CQS & DSS & TAS --> POut

    POut --> TPA & CPA & MockBank & MockCard & MockPay
    KConsumer --> TPA

    TPA & CPA --> PG
    MockBank & MockCard & MockPay -.->|"FetchTransactionsPort\n(pull on sync)"| DSS
    KProducer --> KafkaInfra
    KafkaInfra --> KConsumer
```

## Key Flows

| Flow | Path |
|---|---|
| Query customers / transactions | HTTP → Controller → UseCase → PersistenceAdapter → PostgreSQL |
| Sync from mock sources | HTTP → DataSyncController → DataSyncService → MockAdapters → SaveTransactionPort → PostgreSQL |
| Kafka ingestion | HTTP → KafkaGenerateController → Producer → Kafka → Consumer → PostgreSQL |

## Running Locally

| Mode | Command | Database | Kafka |
|---|---|---|---|
| Full stack (Docker) | `docker compose up --build` | PostgreSQL | Yes |
| Dev (no Docker) | `./mvnw spring-boot:run -Dspring-boot.run.profiles=dev` | H2 in-memory | No |

## API Entry Points

| Endpoint | Description |
|---|---|
| `GET  /api/v1/customers` | List all customers |
| `GET  /api/v1/customers/{id}/summary` | Financial summary for a customer |
| `GET  /api/v1/customers/{id}/transactions` | Filtered transaction list |
| `GET  /api/v1/customers/{id}/categories` | Category spend breakdown |
| `POST /api/v1/sync` | Trigger sync from mock data sources |
| `POST /api/v1/kafka/generate` | Publish synthetic transactions to Kafka |
| `GET  /swagger-ui.html` | Interactive API docs |
| `GET  /actuator/health` | Health check |
