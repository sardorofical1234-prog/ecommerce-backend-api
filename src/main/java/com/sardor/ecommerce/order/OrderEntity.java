package com.sardor.ecommerce.order;

import com.sardor.ecommerce.user.UserEntity;
import jakarta.persistence.CascadeType;
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
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderBy;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Entity
@Table(name = "orders", uniqueConstraints = {
        @UniqueConstraint(name = "uk_orders_customer_idempotency", columnNames = {"customer_id", "idempotency_key"})
})
public class OrderEntity {
    private static final long PAYMENT_WINDOW_MINUTES = 30;

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "customer_id", nullable = false)
    private UserEntity customer;

    @Column(name = "idempotency_key", nullable = false, length = 100)
    private String idempotencyKey;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 24)
    private OrderStatus status;

    @Column(name = "total_amount", nullable = false, precision = 14, scale = 2)
    private BigDecimal totalAmount;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "expires_at", nullable = false, updatable = false)
    private Instant expiresAt;

    @Column(name = "closed_at")
    private Instant closedAt;

    @OneToMany(mappedBy = "order", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("id ASC")
    private List<OrderItemEntity> items = new ArrayList<>();

    protected OrderEntity() {
    }

    public OrderEntity(UserEntity customer, String idempotencyKey, BigDecimal totalAmount) {
        this.customer = customer;
        this.idempotencyKey = idempotencyKey;
        this.totalAmount = totalAmount;
        this.status = OrderStatus.PENDING_PAYMENT;
    }

    public void addItem(OrderItemEntity item) {
        items.add(item);
    }

    public void cancel() {
        if (status != OrderStatus.PENDING_PAYMENT) {
            throw new IllegalStateException("Only pending-payment orders can be cancelled");
        }
        status = OrderStatus.CANCELLED;
        closedAt = Instant.now();
    }

    public void expire() {
        if (status != OrderStatus.PENDING_PAYMENT) {
            throw new IllegalStateException("Only pending-payment orders can expire");
        }
        status = OrderStatus.EXPIRED;
        closedAt = Instant.now();
    }

    @PrePersist
    void onCreate() {
        Instant now = Instant.now();
        createdAt = now;
        expiresAt = now.plus(PAYMENT_WINDOW_MINUTES, ChronoUnit.MINUTES);
    }

    public UUID getId() { return id; }
    public UserEntity getCustomer() { return customer; }
    public String getIdempotencyKey() { return idempotencyKey; }
    public OrderStatus getStatus() { return status; }
    public BigDecimal getTotalAmount() { return totalAmount; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getExpiresAt() { return expiresAt; }
    public Instant getClosedAt() { return closedAt; }
    public List<OrderItemEntity> getItems() { return items; }
}
