package com.sardor.ecommerce.payment;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@Validated
@RestController
@RequestMapping("/api/v1/orders/{orderId}/payments")
public class PaymentController {
    private final PaymentService paymentService;

    public PaymentController(PaymentService paymentService) {
        this.paymentService = paymentService;
    }

    @PostMapping
    public PaymentResponse pay(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable("orderId") UUID orderId,
            @RequestHeader("Idempotency-Key")
            @NotBlank
            @Pattern(regexp = "[A-Za-z0-9._:-]{8,100}", message = "Idempotency-Key must be 8-100 safe characters")
            String idempotencyKey,
            @Valid @RequestBody PaymentRequest request
    ) {
        return paymentService.pay(jwt.getSubject(), orderId, idempotencyKey, request);
    }

    @GetMapping
    public List<PaymentResponse> history(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable("orderId") UUID orderId
    ) {
        return paymentService.getPaymentHistory(jwt.getSubject(), orderId);
    }
}
