-- H2 dev schema — applied once at startup via ResourceDatabasePopulator.
-- Uses VARCHAR for type/status/category (H2 has no native enum type).
-- All tables and indexes in a single file; no migration versioning needed
-- because the H2 in-memory database is recreated on every restart.

CREATE TABLE IF NOT EXISTS customers
(
    id    UUID PRIMARY KEY,
    name  TEXT NOT NULL,
    email TEXT NOT NULL UNIQUE
);

-- Accounts created before transactions so the FK below can reference it.
CREATE TABLE IF NOT EXISTS accounts
(
    id          UUID        PRIMARY KEY,
    customer_id UUID        NOT NULL REFERENCES customers (id),
    name        TEXT,
    source      VARCHAR(50) NOT NULL,
    created_at  TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT now()
);

CREATE TABLE IF NOT EXISTS transactions
(
    id             UUID           PRIMARY KEY,
    customer_id    UUID           NOT NULL REFERENCES customers (id),
    account_id     UUID           NOT NULL REFERENCES accounts (id),
    amount         NUMERIC(19, 4) NOT NULL,
    currency_code  VARCHAR(3)     NOT NULL,
    type           VARCHAR(10)    NOT NULL,
    status         VARCHAR(20)    NOT NULL,
    description    TEXT,
    category       VARCHAR(30)    NOT NULL,
    merchant_name  TEXT,
    data_source_id VARCHAR(50)    NOT NULL,
    occurred_at    TIMESTAMP WITH TIME ZONE NOT NULL,
    created_at     TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT now(),
    updated_at     TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT now()
);

CREATE INDEX IF NOT EXISTS idx_accounts_customer   ON accounts     (customer_id);
CREATE INDEX IF NOT EXISTS idx_tx_account_id       ON transactions (account_id);
CREATE INDEX IF NOT EXISTS idx_tx_occurred_at      ON transactions (occurred_at);
CREATE INDEX IF NOT EXISTS idx_tx_category         ON transactions (category);
CREATE INDEX IF NOT EXISTS idx_tx_account_period   ON transactions (account_id,     occurred_at);
CREATE INDEX IF NOT EXISTS idx_tx_customer_period  ON transactions (customer_id,    occurred_at DESC);
CREATE INDEX IF NOT EXISTS idx_tx_source_period    ON transactions (data_source_id, occurred_at DESC);
CREATE INDEX IF NOT EXISTS idx_tx_pending          ON transactions (customer_id,    occurred_at DESC);
