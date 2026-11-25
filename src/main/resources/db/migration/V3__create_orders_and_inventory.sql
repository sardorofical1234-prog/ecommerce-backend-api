CREATE TABLE orders (
    id UUID PRIMARY KEY,
    customer_id UUID NOT NULL,
    idempotency_key VARCHAR(100) NOT NULL,
    status VARCHAR(24) NOT NULL,
    total_amount NUMERIC(14, 2) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL,
    expires_at TIMESTAMPTZ NOT NULL,
    closed_at TIMESTAMPTZ,
    CONSTRAINT fk_orders_customer
        FOREIGN KEY (customer_id) REFERENCES app_users (id) ON DELETE RESTRICT,
    CONSTRAINT uk_orders_customer_idempotency UNIQUE (customer_id, idempotency_key),
    CONSTRAINT chk_orders_status CHECK (status IN ('PENDING_PAYMENT', 'CANCELLED', 'EXPIRED')),
    CONSTRAINT chk_orders_total_non_negative CHECK (total_amount >= 0)
);

CREATE INDEX idx_orders_customer_created_at ON orders (customer_id, created_at DESC);
CREATE INDEX idx_orders_pending_expiration
    ON orders (expires_at)
    WHERE status = 'PENDING_PAYMENT';

CREATE TABLE order_items (
    id UUID PRIMARY KEY,
    order_id UUID NOT NULL,
    product_id UUID NOT NULL,
    product_sku VARCHAR(64) NOT NULL,
    product_name VARCHAR(180) NOT NULL,
    unit_price NUMERIC(12, 2) NOT NULL,
    quantity INTEGER NOT NULL,
    line_total NUMERIC(14, 2) NOT NULL,
    CONSTRAINT fk_order_items_order
        FOREIGN KEY (order_id) REFERENCES orders (id) ON DELETE CASCADE,
    CONSTRAINT fk_order_items_product
        FOREIGN KEY (product_id) REFERENCES products (id) ON DELETE RESTRICT,
    CONSTRAINT chk_order_items_price_positive CHECK (unit_price > 0),
    CONSTRAINT chk_order_items_quantity_positive CHECK (quantity > 0),
    CONSTRAINT chk_order_items_total_positive CHECK (line_total > 0)
);
