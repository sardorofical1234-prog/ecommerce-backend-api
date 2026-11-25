package com.sardor.ecommerce.cart;

import java.math.BigDecimal;

public record CartItemResponse(
        String productId,
        String sku,
        String productName,
        BigDecimal unitPrice,
        int quantity,
        BigDecimal lineTotal,
        int availableStock,
        boolean available
) {
}
