-- Composite index for the most common query pattern: customer transactions within a time window.
-- Used by customer summary, category breakdown, and filtered search.
CREATE INDEX IF NOT EXISTS idx_tx_customer_period
    ON transactions (customer_id, occurred_at DESC);

-- Composite index for data source filtering within a time window.
-- Used when querying by source (PLAID, KAFKA, etc.) over a date range.
CREATE INDEX IF NOT EXISTS idx_tx_source_period
    ON transactions (data_source_id, occurred_at DESC);
