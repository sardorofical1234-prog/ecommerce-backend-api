package com.sardor.ecommerce.order;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class OrderExpirationJob {
    private final CheckoutService checkoutService;

    public OrderExpirationJob(CheckoutService checkoutService) {
        this.checkoutService = checkoutService;
    }

    @Scheduled(fixedDelayString = "${app.orders.expiration-scan-delay:60000}")
    public void expirePendingOrders() {
        checkoutService.expireExpiredOrders();
    }
}
