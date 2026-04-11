-- ── 1. PostgreSQL enum types ───────────────────────────────────────────────────
-- Must match TransactionCategory, TransactionType, TransactionStatus Java enums exactly.
CREATE TYPE transaction_type AS ENUM ('CREDIT', 'DEBIT');

CREATE TYPE transaction_status AS ENUM ('COMPLETED', 'PENDING', 'FAILED');

CREATE TYPE transaction_category AS ENUM (
    'SALARY', 'FOOD_AND_DINING', 'TRANSPORT', 'UTILITIES', 'SHOPPING',
    'ENTERTAINMENT', 'HEALTHCARE', 'TRANSFER', 'INSURANCE', 'EDUCATION',
    'RENT_AND_MORTGAGE', 'OTHER'
);

ALTER TABLE transactions
    ALTER COLUMN type     TYPE transaction_type     USING type::transaction_type,
    ALTER COLUMN status   TYPE transaction_status   USING status::transaction_status,
    ALTER COLUMN category TYPE transaction_category USING category::transaction_category;

-- ── 2. Accounts table ──────────────────────────────────────────────────────────
-- FK constraint is NOT added here — existing transactions have account_ids with no
-- corresponding account row yet. V5 populates accounts first, then adds the FK.
CREATE TABLE IF NOT EXISTS accounts
(
    id          UUID        PRIMARY KEY,
    customer_id UUID        NOT NULL REFERENCES customers (id),
    name        TEXT,
    source      VARCHAR(50) NOT NULL,
    created_at  TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT now()
);

CREATE INDEX IF NOT EXISTS idx_accounts_customer ON accounts (customer_id);

-- ── 3. Audit timestamps ────────────────────────────────────────────────────────
ALTER TABLE transactions
    ADD COLUMN created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT now(),
    ADD COLUMN updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT now();

CREATE OR REPLACE FUNCTION set_updated_at()
RETURNS TRIGGER LANGUAGE plpgsql AS $$
BEGIN
    NEW.updated_at = now();
    RETURN NEW;
END;
$$;

CREATE TRIGGER trg_transactions_updated_at
    BEFORE UPDATE ON transactions
    FOR EACH ROW EXECUTE FUNCTION set_updated_at();

-- ── 4. Drop redundant single-column index ──────────────────────────────────────
DROP INDEX IF EXISTS idx_tx_customer_id;

-- ── 5. GIN full-text search index ─────────────────────────────────────────────
CREATE INDEX IF NOT EXISTS idx_tx_fulltext
    ON transactions
    USING GIN (to_tsvector('english', coalesce(description, '') || ' ' || coalesce(merchant_name, '')));

-- ── 6. Partial index for PENDING transactions ──────────────────────────────────
CREATE INDEX IF NOT EXISTS idx_tx_pending
    ON transactions (customer_id, occurred_at DESC)
    WHERE status = 'PENDING';
