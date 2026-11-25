package com.sardor.ecommerce.order;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@Validated
@RestController
@RequestMapping("/api/v1/checkout")
public class CheckoutController {
    private final CheckoutService checkoutService;

    public CheckoutController(CheckoutService checkoutService) {
        this.checkoutService = checkoutService;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public OrderResponse checkout(
            @AuthenticationPrincipal Jwt jwt,
            @RequestHeader("Idempotency-Key")
            @NotBlank
            @Pattern(regexp = "[A-Za-z0-9._:-]{8,100}", message = "Idempotency-Key must be 8-100 safe characters")
            String idempotencyKey
    ) {
        return checkoutService.checkout(jwt.getSubject(), idempotencyKey);
    }
}
