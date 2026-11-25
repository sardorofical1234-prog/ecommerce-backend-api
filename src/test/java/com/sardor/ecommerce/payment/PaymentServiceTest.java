package com.sardor.ecommerce.payment;

import com.sardor.ecommerce.common.ConflictException;
import com.sardor.ecommerce.order.OrderEntity;
import com.sardor.ecommerce.order.OrderRepository;
import com.sardor.ecommerce.user.UserEntity;
import com.sardor.ecommerce.user.UserRepository;
import com.sardor.ecommerce.user.UserRole;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PaymentServiceTest {
    private static final String CUSTOMER_EMAIL = "buyer@example.com";
    private static final String IDEMPOTENCY_KEY = "payment-attempt-001";

    @Mock UserRepository userRepository;
    @Mock OrderRepository orderRepository;
    @Mock PaymentAttemptRepository paymentAttemptRepository;
    @Mock PaymentProvider paymentProvider;
    @InjectMocks PaymentService paymentService;

    private UserEntity customer;
    private OrderEntity order;

    @BeforeEach
    void setUp() {
        customer = new UserEntity(CUSTOMER_EMAIL, "hash", "Test Buyer", UserRole.CUSTOMER);
        ReflectionTestUtils.setField(customer, "id", UUID.randomUUID());

        order = new OrderEntity(customer, "checkout-key-001", new BigDecimal("159.80"));
        ReflectionTestUtils.setField(order, "id", UUID.randomUUID());
        ReflectionTestUtils.setField(order, "createdAt", Instant.now());
        ReflectionTestUtils.setField(order, "expiresAt", Instant.now().plus(30, ChronoUnit.MINUTES));

        when(userRepository.findByEmailForUpdate(CUSTOMER_EMAIL)).thenReturn(Optional.of(customer));
        when(orderRepository.findByIdAndCustomerIdForUpdate(order.getId(), customer.getId()))
                .thenReturn(Optional.of(order));
    }

    @Test
    void approvedPaymentMarksOrderPaidAndPersistsAttempt() {
        when(paymentProvider.charge(order.getId(), order.getTotalAmount(), PaymentScenario.APPROVE))
                .thenReturn(new PaymentProviderResult(true, "SIM-APPROVED-001", null, null));
        when(paymentAttemptRepository.saveAndFlush(any(PaymentAttemptEntity.class)))
                .thenAnswer(invocation -> persistAttempt(invocation.getArgument(0)));

        PaymentResponse response = paymentService.pay(CUSTOMER_EMAIL, order.getId(), IDEMPOTENCY_KEY,
                new PaymentRequest(PaymentScenario.APPROVE));

        assertEquals("SUCCEEDED", response.status());
        assertEquals(new BigDecimal("159.80"), response.amount());
        assertEquals("SIM-APPROVED-001", response.providerReference());
        assertEquals("PAID", order.getStatus().name());
        assertNotNull(order.getPaidAt());
        verify(paymentProvider).charge(order.getId(), new BigDecimal("159.80"), PaymentScenario.APPROVE);
    }

    @Test
    void declinedPaymentKeepsOrderPendingAndPersistsFailureDetails() {
        when(paymentProvider.charge(order.getId(), order.getTotalAmount(), PaymentScenario.DECLINE))
                .thenReturn(new PaymentProviderResult(false, "SIM-DECLINED-001",
                        "SIMULATED_DECLINE", "The simulated payment was declined."));
        when(paymentAttemptRepository.saveAndFlush(any(PaymentAttemptEntity.class)))
                .thenAnswer(invocation -> persistAttempt(invocation.getArgument(0)));

        PaymentResponse response = paymentService.pay(CUSTOMER_EMAIL, order.getId(), IDEMPOTENCY_KEY,
                new PaymentRequest(PaymentScenario.DECLINE));

        assertEquals("DECLINED", response.status());
        assertEquals("SIMULATED_DECLINE", response.failureCode());
        assertEquals("PENDING_PAYMENT", order.getStatus().name());
        verify(paymentAttemptRepository).saveAndFlush(any(PaymentAttemptEntity.class));
    }

    @Test
    void declinedAttemptCanBeFollowedBySuccessfulAttemptWithNewKey() {
        when(paymentProvider.charge(order.getId(), order.getTotalAmount(), PaymentScenario.DECLINE))
                .thenReturn(new PaymentProviderResult(false, "SIM-DECLINED-002",
                        "SIMULATED_DECLINE", "The simulated payment was declined."));
        when(paymentProvider.charge(order.getId(), order.getTotalAmount(), PaymentScenario.APPROVE))
                .thenReturn(new PaymentProviderResult(true, "SIM-APPROVED-002", null, null));
        when(paymentAttemptRepository.saveAndFlush(any(PaymentAttemptEntity.class)))
                .thenAnswer(invocation -> persistAttempt(invocation.getArgument(0)));

        PaymentResponse declined = paymentService.pay(CUSTOMER_EMAIL, order.getId(), IDEMPOTENCY_KEY,
                new PaymentRequest(PaymentScenario.DECLINE));
        PaymentResponse approved = paymentService.pay(CUSTOMER_EMAIL, order.getId(), "payment-attempt-002",
                new PaymentRequest(PaymentScenario.APPROVE));

        assertEquals("DECLINED", declined.status());
        assertEquals("SUCCEEDED", approved.status());
        assertEquals("PAID", order.getStatus().name());
        verify(paymentAttemptRepository, org.mockito.Mockito.times(2)).saveAndFlush(any(PaymentAttemptEntity.class));
    }

    @Test
    void sameIdempotencyKeyReturnsPreviousAttemptWithoutCallingProvider() {
        PaymentProviderResult result = new PaymentProviderResult(false, "SIM-DECLINED-OLD",
                "SIMULATED_DECLINE", "The simulated payment was declined.");
        PaymentAttemptEntity previous = new PaymentAttemptEntity(order, IDEMPOTENCY_KEY, order.getTotalAmount(), result);
        ReflectionTestUtils.setField(previous, "id", UUID.randomUUID());
        ReflectionTestUtils.setField(previous, "createdAt", Instant.now());
        ReflectionTestUtils.setField(previous, "completedAt", Instant.now());
        when(paymentAttemptRepository.findByOrderIdAndIdempotencyKey(order.getId(), IDEMPOTENCY_KEY))
                .thenReturn(Optional.of(previous));

        PaymentResponse response = paymentService.pay(CUSTOMER_EMAIL, order.getId(), IDEMPOTENCY_KEY,
                new PaymentRequest(PaymentScenario.APPROVE));

        assertEquals("DECLINED", response.status());
        assertEquals("SIM-DECLINED-OLD", response.providerReference());
        verify(paymentProvider, never()).charge(any(), any(), any());
        verify(paymentAttemptRepository, never()).saveAndFlush(any());
    }

    @Test
    void aDifferentAttemptCannotChargeAnAlreadyPaidOrder() {
        order.markPaid();

        assertThrows(ConflictException.class, () -> paymentService.pay(CUSTOMER_EMAIL, order.getId(),
                IDEMPOTENCY_KEY, new PaymentRequest(PaymentScenario.APPROVE)));

        verify(paymentProvider, never()).charge(any(), any(), any());
        verify(paymentAttemptRepository, never()).saveAndFlush(any());
    }

    @Test
    void expiredOrderCannotBeCharged() {
        ReflectionTestUtils.setField(order, "expiresAt", Instant.now().minus(1, ChronoUnit.MINUTES));

        assertThrows(ConflictException.class, () -> paymentService.pay(CUSTOMER_EMAIL, order.getId(),
                IDEMPOTENCY_KEY, new PaymentRequest(PaymentScenario.APPROVE)));

        verify(paymentProvider, never()).charge(any(), any(), any());
        verify(paymentAttemptRepository, never()).saveAndFlush(any());
    }

    private PaymentAttemptEntity persistAttempt(PaymentAttemptEntity attempt) {
        ReflectionTestUtils.setField(attempt, "id", UUID.randomUUID());
        ReflectionTestUtils.setField(attempt, "createdAt", Instant.now());
        ReflectionTestUtils.setField(attempt, "completedAt", Instant.now());
        return attempt;
    }
}
