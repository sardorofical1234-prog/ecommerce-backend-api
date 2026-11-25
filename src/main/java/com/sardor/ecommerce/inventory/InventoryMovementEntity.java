package com.sardor.ecommerce.inventory;

import com.sardor.ecommerce.order.OrderEntity;
import com.sardor.ecommerce.product.ProductEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "inventory_movements")
public class InventoryMovementEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "product_id", nullable = false)
    private ProductEntity product;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "order_id", nullable = false)
    private OrderEntity order;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private InventoryMovementType type;

    @Column(nullable = false)
    private int quantity;

    @Column(name = "stock_after", nullable = false)
    private int stockAfter;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    protected InventoryMovementEntity() {
    }

    public InventoryMovementEntity(ProductEntity product, OrderEntity order,
                                   InventoryMovementType type, int quantity, int stockAfter) {
        this.product = product;
        this.order = order;
        this.type = type;
        this.quantity = quantity;
        this.stockAfter = stockAfter;
    }

    @PrePersist
    void onCreate() {
        createdAt = Instant.now();
    }

    public UUID getId() { return id; }
    public ProductEntity getProduct() { return product; }
    public OrderEntity getOrder() { return order; }
    public InventoryMovementType getType() { return type; }
    public int getQuantity() { return quantity; }
    public int getStockAfter() { return stockAfter; }
    public Instant getCreatedAt() { return createdAt; }
}
