package com.sardor.ecommerce.product;

import java.math.BigDecimal;
import java.time.Instant;

public record ProductResponse(
        String id,
        String sku,
        String name,
        String description,
        String category,
        BigDecimal price,
        int stock,
        Instant createdAt,
        Instant updatedAt
) {
    public static ProductResponse from(ProductEntity product) {
        return new ProductResponse(product.getId().toString(), product.getSku(), product.getName(),
                product.getDescription(), product.getCategory(), product.getPrice(), product.getStock(),
                product.getCreatedAt(), product.getUpdatedAt());
    }
}
