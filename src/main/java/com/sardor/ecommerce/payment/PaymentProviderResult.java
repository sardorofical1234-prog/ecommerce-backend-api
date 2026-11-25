package com.sardor.ecommerce.payment;

public record PaymentProviderResult(
        boolean approved,
        String providerReference,
        String failureCode,
        String failureMessage
) {
}
