package com.sardor.ecommerce.order;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

public record OrderResponse(
        String id,
        String status,
        BigDecimal totalAmount,
        Instant createdAt,
        Instant expiresAt,
        Instant closedAt,
        List<OrderItemResponse> items
) {
    static OrderResponse from(OrderEntity order) {
        return new OrderResponse(order.getId().toString(), order.getStatus().name(), order.getTotalAmount(),
                order.getCreatedAt(), order.getExpiresAt(), order.getClosedAt(),
                order.getItems().stream().map(OrderItemResponse::from).toList());
    }
}
