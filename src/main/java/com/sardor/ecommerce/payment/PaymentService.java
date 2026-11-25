package com.sardor.ecommerce.payment;

import com.sardor.ecommerce.common.ConflictException;
import com.sardor.ecommerce.common.NotFoundException;
import com.sardor.ecommerce.common.UnauthorizedException;
import com.sardor.ecommerce.order.OrderEntity;
import com.sardor.ecommerce.order.OrderRepository;
import com.sardor.ecommerce.order.OrderStatus;
import com.sardor.ecommerce.user.UserEntity;
import com.sardor.ecommerce.user.UserRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Service
public class PaymentService {
    private final UserRepository userRepository;
    private final OrderRepository orderRepository;
    private final PaymentAttemptRepository paymentAttemptRepository;
    private final PaymentProvider paymentProvider;

    public PaymentService(UserRepository userRepository, OrderRepository orderRepository,
                          PaymentAttemptRepository paymentAttemptRepository, PaymentProvider paymentProvider) {
        this.userRepository = userRepository;
        this.orderRepository = orderRepository;
        this.paymentAttemptRepository = paymentAttemptRepository;
        this.paymentProvider = paymentProvider;
    }

    @Transactional
    public PaymentResponse pay(String customerEmail, UUID orderId, String idempotencyKey,
                              PaymentRequest request) {
        UserEntity customer = userRepository.findByEmailForUpdate(customerEmail)
                .orElseThrow(() -> new UnauthorizedException("Authenticated customer account was not found"));
        OrderEntity order = orderRepository.findByIdAndCustomerIdForUpdate(orderId, customer.getId())
                .orElseThrow(() -> new NotFoundException("Order not found"));

        var previousAttempt = paymentAttemptRepository.findByOrderIdAndIdempotencyKey(orderId, idempotencyKey);
        if (previousAttempt.isPresent()) {
            return PaymentResponse.from(previousAttempt.get());
        }

        if (order.getStatus() != OrderStatus.PENDING_PAYMENT) {
            throw new ConflictException("Only pending-payment orders can be paid");
        }
        if (order.getExpiresAt() == null || !order.getExpiresAt().isAfter(Instant.now())) {
            throw new ConflictException("The payment window has expired; the order will be closed by the expiration job");
        }

        PaymentProviderResult result = paymentProvider.charge(orderId, order.getTotalAmount(), idempotencyKey, request.scenario());
        PaymentAttemptEntity attempt = new PaymentAttemptEntity(order, idempotencyKey,
                order.getTotalAmount(), result);
        if (result.approved()) {
            order.markPaid();
        }

        PaymentAttemptEntity savedAttempt = paymentAttemptRepository.saveAndFlush(attempt);
        return PaymentResponse.from(savedAttempt);
    }

    @Transactional(readOnly = true)
    public List<PaymentResponse> getPaymentHistory(String customerEmail, UUID orderId) {
        UserEntity customer = userRepository.findByEmail(customerEmail)
                .orElseThrow(() -> new UnauthorizedException("Authenticated customer account was not found"));
        orderRepository.findByIdAndCustomerId(orderId, customer.getId())
                .orElseThrow(() -> new NotFoundException("Order not found"));

        return paymentAttemptRepository.findByOrderIdOrderByCreatedAtDesc(orderId)
                .stream().map(PaymentResponse::from).toList();
    }
}
