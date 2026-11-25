package com.sardor.ecommerce.payment;

import java.math.BigDecimal;
import java.util.UUID;

public interface PaymentProvider {
    /**
     * The adapter must forward the idempotency key to the upstream provider.
     * This lets a real gateway safely deduplicate retries after an ambiguous timeout.
     */
    PaymentProviderResult charge(UUID orderId, BigDecimal amount, String idempotencyKey,
                                 PaymentScenario scenario);
}
