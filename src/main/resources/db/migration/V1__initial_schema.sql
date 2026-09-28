CREATE TABLE wallets (
    id          UUID        NOT NULL DEFAULT gen_random_uuid(),
    user_id     UUID        NOT NULL,
    balance     NUMERIC(19, 4) NOT NULL DEFAULT 0,
    currency    CHAR(3)     NOT NULL DEFAULT 'BRL',
    version     BIGINT      NOT NULL DEFAULT 0,
    created_at  TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at  TIMESTAMPTZ NOT NULL DEFAULT NOW(),

    CONSTRAINT pk_wallets PRIMARY KEY (id),
    CONSTRAINT uq_wallets_user_id UNIQUE (user_id),
    CONSTRAINT chk_wallets_balance CHECK (balance >= 0)
);

CREATE TABLE transactions (
    id                   UUID           NOT NULL DEFAULT gen_random_uuid(),
    wallet_id            UUID           NOT NULL,
    type                 VARCHAR(20)    NOT NULL,
    amount               NUMERIC(19, 4) NOT NULL,
    balance_after        NUMERIC(19, 4) NOT NULL,
    currency             CHAR(3)        NOT NULL,
    correlation_id       UUID,
    counterpart_wallet_id UUID,
    description          VARCHAR(255),
    created_at           TIMESTAMPTZ    NOT NULL DEFAULT NOW(),

    CONSTRAINT pk_transactions PRIMARY KEY (id),
    CONSTRAINT fk_transactions_wallet FOREIGN KEY (wallet_id) REFERENCES wallets(id),
    CONSTRAINT chk_transactions_type CHECK (type IN ('DEPOSIT', 'WITHDRAWAL', 'TRANSFER_DEBIT', 'TRANSFER_CREDIT')),
    CONSTRAINT chk_transactions_balance_after CHECK (balance_after >= 0)
);

CREATE INDEX idx_transactions_wallet_id   ON transactions(wallet_id);
CREATE INDEX idx_transactions_created_at  ON transactions(created_at);
CREATE INDEX idx_transactions_correlation ON transactions(correlation_id);
-- Composite index optimised for the historical balance query
CREATE INDEX idx_transactions_wallet_created ON transactions(wallet_id, created_at DESC);

COMMENT ON TABLE wallets      IS 'Current wallet state - one per user';
COMMENT ON TABLE transactions IS 'Immutable append-only ledger. Never update or delete rows.';
COMMENT ON COLUMN transactions.balance_after IS 'Wallet balance snapshot after this operation. Enables O(1) historical balance lookup.';
COMMENT ON COLUMN transactions.correlation_id IS 'Links the two sides of a transfer (debit + credit share the same UUID).';
