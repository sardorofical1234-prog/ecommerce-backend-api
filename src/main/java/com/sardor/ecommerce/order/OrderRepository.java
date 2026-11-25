package com.sardor.ecommerce.order;

import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface OrderRepository extends JpaRepository<OrderEntity, UUID> {

    @EntityGraph(attributePaths = "items")
    Optional<OrderEntity> findByCustomerIdAndIdempotencyKey(UUID customerId, String idempotencyKey);

    @EntityGraph(attributePaths = "items")
    Optional<OrderEntity> findByIdAndCustomerId(UUID orderId, UUID customerId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select o from OrderEntity o where o.id = :orderId and o.customer.id = :customerId")
    Optional<OrderEntity> findByIdAndCustomerIdForUpdate(
            @Param("orderId") UUID orderId,
            @Param("customerId") UUID customerId
    );

    @EntityGraph(attributePaths = "items")
    List<OrderEntity> findByCustomerIdOrderByCreatedAtDesc(UUID customerId, Pageable pageable);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
            select o from OrderEntity o
            where o.status = com.sardor.ecommerce.order.OrderStatus.PENDING_PAYMENT
              and o.expiresAt <= :now
            order by o.expiresAt asc
            """)
    List<OrderEntity> findExpiredPendingOrdersForUpdate(@Param("now") Instant now, Pageable pageable);
}
