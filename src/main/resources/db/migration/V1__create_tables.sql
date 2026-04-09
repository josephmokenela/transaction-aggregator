-- ── Customers ─────────────────────────────────────────────────────────────────
CREATE TABLE IF NOT EXISTS customers
(
    id    UUID PRIMARY KEY,
    name  TEXT NOT NULL,
    email TEXT NOT NULL UNIQUE
);

-- ── Transactions ───────────────────────────────────────────────────────────────
-- amount is stored with 4 decimal places to handle non-standard currencies
-- without precision loss; the domain model normalises scale via Money.of().
CREATE TABLE IF NOT EXISTS transactions
(
    id             UUID         PRIMARY KEY,
    customer_id    UUID         NOT NULL REFERENCES customers (id),
    account_id     UUID         NOT NULL,
    amount         NUMERIC(19, 4) NOT NULL,
    currency_code  VARCHAR(3)   NOT NULL,
    type           VARCHAR(10)  NOT NULL,   -- CREDIT | DEBIT
    status         VARCHAR(20)  NOT NULL,   -- COMPLETED | PENDING | FAILED
    description    TEXT,
    category       VARCHAR(30)  NOT NULL,
    merchant_name  TEXT,
    data_source_id VARCHAR(50)  NOT NULL,
    occurred_at    TIMESTAMP WITH TIME ZONE  NOT NULL
);

-- Covering indexes for the query patterns used by LoadTransactionPort
CREATE INDEX IF NOT EXISTS idx_tx_customer_id  ON transactions (customer_id);
CREATE INDEX IF NOT EXISTS idx_tx_account_id   ON transactions (account_id);
CREATE INDEX IF NOT EXISTS idx_tx_occurred_at  ON transactions (occurred_at);
CREATE INDEX IF NOT EXISTS idx_tx_category     ON transactions (category);
-- Composite index speeds up the aggregate query (account + time window)
CREATE INDEX IF NOT EXISTS idx_tx_account_period ON transactions (account_id, occurred_at);
