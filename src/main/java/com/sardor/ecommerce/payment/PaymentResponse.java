package com.sardor.ecommerce.payment;

import java.math.BigDecimal;
import java.time.Instant;

public record PaymentResponse(
        String id,
        String orderId,
        String status,
        BigDecimal amount,
        String providerReference,
        String failureCode,
        String failureMessage,
        Instant createdAt,
        Instant completedAt
) {
    static PaymentResponse from(PaymentAttemptEntity attempt) {
        return new PaymentResponse(attempt.getId().toString(), attempt.getOrder().getId().toString(),
                attempt.getStatus().name(), attempt.getAmount(), attempt.getProviderReference(),
                attempt.getFailureCode(), attempt.getFailureMessage(),
                attempt.getCreatedAt(), attempt.getCompletedAt());
    }
}
