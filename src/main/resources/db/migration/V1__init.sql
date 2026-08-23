-- V1__init.sql
-- P2P Wallet schema: wallets + transfers

CREATE TABLE wallets (
    id          UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id     VARCHAR(255) NOT NULL UNIQUE,
    balance     BIGINT NOT NULL DEFAULT 0
                CONSTRAINT balance_non_negative CHECK (balance >= 0),
    created_at  TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at  TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE TABLE transfers (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    idempotency_key VARCHAR(255) NOT NULL UNIQUE,
    from_wallet_id  UUID NOT NULL REFERENCES wallets(id),
    to_wallet_id    UUID NOT NULL REFERENCES wallets(id),
    amount_paise    BIGINT NOT NULL CONSTRAINT amount_positive CHECK (amount_paise > 0),
    status          VARCHAR(20) NOT NULL DEFAULT 'COMPLETED',
    decline_reason  VARCHAR(255),
    created_at      TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX idx_transfers_from ON transfers(from_wallet_id);
CREATE INDEX idx_transfers_to   ON transfers(to_wallet_id);
