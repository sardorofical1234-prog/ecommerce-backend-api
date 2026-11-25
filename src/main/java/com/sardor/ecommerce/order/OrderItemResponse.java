package com.sardor.ecommerce.order;

import java.math.BigDecimal;

public record OrderItemResponse(
        String productId,
        String sku,
        String productName,
        BigDecimal unitPrice,
        int quantity,
        BigDecimal lineTotal
) {
    static OrderItemResponse from(OrderItemEntity item) {
        return new OrderItemResponse(item.getProductId().toString(), item.getProductSku(),
                item.getProductName(), item.getUnitPrice(), item.getQuantity(), item.getLineTotal());
    }
}
