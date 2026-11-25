package com.sardor.ecommerce.payment;

import java.math.BigDecimal;
import java.util.UUID;

public interface PaymentProvider {
    PaymentProviderResult charge(UUID orderId, BigDecimal amount, PaymentScenario scenario);
}
