# Architecture

## High-Level Overview

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
