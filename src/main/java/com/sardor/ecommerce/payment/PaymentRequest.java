package com.sardor.ecommerce.payment;

import jakarta.validation.constraints.NotNull;

public record PaymentRequest(
        @NotNull PaymentScenario scenario
) {
}
