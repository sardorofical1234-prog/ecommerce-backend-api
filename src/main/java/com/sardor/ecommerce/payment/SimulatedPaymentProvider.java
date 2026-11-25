package com.sardor.ecommerce.payment;

import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.UUID;

@Component
public class SimulatedPaymentProvider implements PaymentProvider {

    @Override
    public PaymentProviderResult charge(UUID orderId, BigDecimal amount, String idempotencyKey,
                                         PaymentScenario scenario) {
        // The demo provider is local; real provider adapters must send idempotencyKey upstream.
        String reference = "SIM-" + UUID.randomUUID();
        if (scenario == PaymentScenario.APPROVE) {
            return new PaymentProviderResult(true, reference, null, null);
        }
        return new PaymentProviderResult(false, reference,
                "SIMULATED_DECLINE", "The simulated payment was declined.");
    }
}
