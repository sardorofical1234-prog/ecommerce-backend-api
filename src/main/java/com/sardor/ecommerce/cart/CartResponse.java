package com.sardor.ecommerce.cart;

import java.math.BigDecimal;
import java.util.List;

public record CartResponse(
        String id,
        List<CartItemResponse> items,
        int totalItems,
        BigDecimal estimatedTotal
) {
}
