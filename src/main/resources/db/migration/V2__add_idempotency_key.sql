ALTER TABLE transactions ADD COLUMN idempotency_key UUID;

-- Partial unique index: allows multiple NULLs, enforces uniqueness only for non-null values
CREATE UNIQUE INDEX idx_transactions_idempotency_key
    ON transactions(idempotency_key)
    WHERE idempotency_key IS NOT NULL;

COMMENT ON COLUMN transactions.idempotency_key IS 'Client-provided key for idempotent retries; unique when present.';
