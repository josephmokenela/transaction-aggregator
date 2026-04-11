-- ── 1. Populate accounts from existing transactions ───────────────────────────
-- Every distinct account_id already present in transactions gets a synthetic
-- account row so the FK constraint added below can be satisfied.
INSERT INTO accounts (id, customer_id, source, created_at)
SELECT DISTINCT
    account_id,
    customer_id,
    data_source_id,
    now()
FROM transactions
ON CONFLICT (id) DO NOTHING;

-- ── 2. Add FK constraint now that accounts are fully populated ─────────────────
ALTER TABLE transactions
    ADD CONSTRAINT fk_tx_account
    FOREIGN KEY (account_id) REFERENCES accounts (id);
