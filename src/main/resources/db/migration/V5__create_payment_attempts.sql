ALTER TABLE orders
    DROP CONSTRAINT chk_orders_status;

ALTER TABLE orders
    ADD CONSTRAINT chk_orders_status
        CHECK (status IN ('PENDING_PAYMENT', 'CANCELLED', 'EXPIRED', 'PAID'));

ALTER TABLE orders
    ADD COLUMN paid_at TIMESTAMPTZ;

CREATE TABLE payment_attempts (
    id UUID PRIMARY KEY,
    order_id UUID NOT NULL,
    idempotency_key VARCHAR(100) NOT NULL,
    status VARCHAR(16) NOT NULL,
    amount NUMERIC(14, 2) NOT NULL,
    provider_reference VARCHAR(64) NOT NULL UNIQUE,
    failure_code VARCHAR(64),
    failure_message VARCHAR(255),
    created_at TIMESTAMPTZ NOT NULL,
    completed_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT fk_payment_attempts_order
        FOREIGN KEY (order_id) REFERENCES orders (id) ON DELETE RESTRICT,
    CONSTRAINT uk_payment_attempts_order_idempotency UNIQUE (order_id, idempotency_key),
    CONSTRAINT chk_payment_attempts_status CHECK (status IN ('SUCCEEDED', 'DECLINED')),
    CONSTRAINT chk_payment_attempts_amount_positive CHECK (amount > 0)
);

CREATE INDEX idx_payment_attempts_order_created_at
    ON payment_attempts (order_id, created_at DESC);

CREATE UNIQUE INDEX uk_payment_attempts_one_success_per_order
    ON payment_attempts (order_id)
    WHERE status = 'SUCCEEDED';
