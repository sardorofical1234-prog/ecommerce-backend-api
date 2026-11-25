package com.sardor.ecommerce.order;

import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/orders")
public class OrderController {
    private final OrderService orderService;
    private final CheckoutService checkoutService;

    public OrderController(OrderService orderService, CheckoutService checkoutService) {
        this.orderService = orderService;
        this.checkoutService = checkoutService;
    }

    @GetMapping
    public List<OrderResponse> getMyOrders(@AuthenticationPrincipal Jwt jwt) {
        return orderService.getMyOrders(jwt.getSubject(), PageRequest.of(0, 50));
    }

    @GetMapping("/{orderId}")
    public OrderResponse getOrder(@AuthenticationPrincipal Jwt jwt, @PathVariable("orderId") UUID orderId) {
        return orderService.getOrder(jwt.getSubject(), orderId);
    }

    @PostMapping("/{orderId}/cancel")
    @ResponseStatus(HttpStatus.OK)
    public OrderResponse cancel(@AuthenticationPrincipal Jwt jwt, @PathVariable("orderId") UUID orderId) {
        return checkoutService.cancel(jwt.getSubject(), orderId);
    }
}
