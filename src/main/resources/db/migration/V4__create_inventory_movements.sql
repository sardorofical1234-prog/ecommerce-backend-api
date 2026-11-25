CREATE TABLE inventory_movements (
    id UUID PRIMARY KEY,
    product_id UUID NOT NULL,
    order_id UUID NOT NULL,
    type VARCHAR(16) NOT NULL,
    quantity INTEGER NOT NULL,
    stock_after INTEGER NOT NULL,
    created_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT fk_inventory_movements_product
        FOREIGN KEY (product_id) REFERENCES products (id) ON DELETE RESTRICT,
    CONSTRAINT fk_inventory_movements_order
        FOREIGN KEY (order_id) REFERENCES orders (id) ON DELETE RESTRICT,
    CONSTRAINT chk_inventory_movements_type CHECK (type IN ('RESERVED', 'RELEASED')),
    CONSTRAINT chk_inventory_movements_quantity CHECK (quantity > 0),
    CONSTRAINT chk_inventory_movements_stock CHECK (stock_after >= 0)
);

CREATE INDEX idx_inventory_movements_product_created_at
    ON inventory_movements (product_id, created_at DESC);
CREATE INDEX idx_inventory_movements_order_id
    ON inventory_movements (order_id);
