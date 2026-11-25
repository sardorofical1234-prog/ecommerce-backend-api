package com.sardor.ecommerce.order;

import com.sardor.ecommerce.cart.ShoppingCartEntity;
import com.sardor.ecommerce.cart.ShoppingCartRepository;
import com.sardor.ecommerce.common.ConflictException;
import com.sardor.ecommerce.inventory.InventoryMovementRepository;
import com.sardor.ecommerce.product.ProductEntity;
import com.sardor.ecommerce.product.ProductRepository;
import com.sardor.ecommerce.user.UserEntity;
import com.sardor.ecommerce.user.UserRepository;
import com.sardor.ecommerce.user.UserRole;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Pageable;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CheckoutServiceTest {
    private static final String CUSTOMER_EMAIL = "buyer@example.com";
    private static final String IDEMPOTENCY_KEY = "checkout-2026-001";

    @Mock UserRepository userRepository;
    @Mock ShoppingCartRepository cartRepository;
    @Mock ProductRepository productRepository;
    @Mock OrderRepository orderRepository;
    @Mock InventoryMovementRepository inventoryMovementRepository;
    @InjectMocks CheckoutService checkoutService;

    private UserEntity customer;
    private ProductEntity product;
    private ShoppingCartEntity cart;

    @BeforeEach
    void setUp() {
        customer = new UserEntity(CUSTOMER_EMAIL, "hash", "Test Buyer", UserRole.CUSTOMER);
        ReflectionTestUtils.setField(customer, "id", UUID.randomUUID());

        product = new ProductEntity("KB-100", "Mechanical Keyboard", "Hot-swappable keyboard",
                "Accessories", new BigDecimal("79.90"), 10);
        ReflectionTestUtils.setField(product, "id", UUID.randomUUID());

        cart = new ShoppingCartEntity(customer);
        ReflectionTestUtils.setField(cart, "id", UUID.randomUUID());
        cart.addProduct(product, 2);

        when(userRepository.findByEmailForUpdate(CUSTOMER_EMAIL)).thenReturn(Optional.of(customer));
    }

    @Test
    void checkoutCreatesOrderSnapshotsPricesReservesStockAndClearsCart() {
        arrangeNewCheckout();

        when(orderRepository.saveAndFlush(any(OrderEntity.class))).thenAnswer(invocation -> {
            OrderEntity order = invocation.getArgument(0);
            ReflectionTestUtils.setField(order, "id", UUID.randomUUID());
            ReflectionTestUtils.setField(order, "createdAt", Instant.now());
            ReflectionTestUtils.setField(order, "expiresAt", Instant.now().plus(30, ChronoUnit.MINUTES));
            return order;
        });

        OrderResponse response = checkoutService.checkout(CUSTOMER_EMAIL, IDEMPOTENCY_KEY);

        assertEquals("PENDING_PAYMENT", response.status());
        assertEquals(new BigDecimal("159.80"), response.totalAmount());
        assertEquals(1, response.items().size());
        assertEquals("KB-100", response.items().getFirst().sku());
        assertEquals(new BigDecimal("79.90"), response.items().getFirst().unitPrice());
        assertEquals(2, response.items().getFirst().quantity());
        assertEquals(8, product.getStock());
        assertEquals(0, cart.getItems().size());
        verify(inventoryMovementRepository).saveAll(anyList());
    }

    @Test
    void checkoutWithSameIdempotencyKeyReturnsPreviousOrderWithoutReservingAgain() {
        OrderEntity previousOrder = new OrderEntity(customer, IDEMPOTENCY_KEY, new BigDecimal("159.80"));
        ReflectionTestUtils.setField(previousOrder, "id", UUID.randomUUID());
        ReflectionTestUtils.setField(previousOrder, "createdAt", Instant.now());
        ReflectionTestUtils.setField(previousOrder, "expiresAt", Instant.now().plus(30, ChronoUnit.MINUTES));
        when(orderRepository.findByCustomerIdAndIdempotencyKey(customer.getId(), IDEMPOTENCY_KEY))
                .thenReturn(Optional.of(previousOrder));

        OrderResponse response = checkoutService.checkout(CUSTOMER_EMAIL, IDEMPOTENCY_KEY);

        assertEquals(previousOrder.getId().toString(), response.id());
        verify(cartRepository, never()).findByUserIdForUpdate(any());
        verify(productRepository, never()).findAllByIdForUpdate(anyList());
        verify(orderRepository, never()).saveAndFlush(any());
        verify(inventoryMovementRepository, never()).saveAll(anyList());
        assertEquals(10, product.getStock());
    }

    @Test
    void checkoutRejectsInsufficientStockAndKeepsCart() {
        product.reserveStock(9);
        arrangeNewCheckout();

        assertThrows(ConflictException.class,
                () -> checkoutService.checkout(CUSTOMER_EMAIL, IDEMPOTENCY_KEY));

        assertEquals(1, product.getStock());
        assertEquals(1, cart.getItems().size());
        verify(orderRepository, never()).saveAndFlush(any());
        verify(inventoryMovementRepository, never()).saveAll(anyList());
    }

    @Test
    void checkoutRejectsAnEmptyCart() {
        cart.getItems().clear();
        arrangeNewCheckout();

        assertThrows(ConflictException.class,
                () -> checkoutService.checkout(CUSTOMER_EMAIL, IDEMPOTENCY_KEY));

        verify(productRepository, never()).findAllByIdForUpdate(anyList());
        verify(orderRepository, never()).saveAndFlush(any());
    }

    @Test
    void cancellationRestoresInventoryOnlyOnce() {
        OrderEntity order = createOrderWithItem();
        when(orderRepository.findByIdAndCustomerIdForUpdate(order.getId(), customer.getId()))
                .thenReturn(Optional.of(order));
        when(productRepository.findAllByIdForUpdate(anyList())).thenReturn(List.of(product));

        OrderResponse first = checkoutService.cancel(CUSTOMER_EMAIL, order.getId());
        OrderResponse second = checkoutService.cancel(CUSTOMER_EMAIL, order.getId());

        assertEquals("CANCELLED", first.status());
        assertEquals("CANCELLED", second.status());
        assertEquals(10, product.getStock());
        verify(inventoryMovementRepository).saveAll(anyList());
    }

    @Test
    void expiredPendingOrderReleasesStockAndChangesStatus() {
        OrderEntity order = createOrderWithItem();
        ReflectionTestUtils.setField(order, "expiresAt", Instant.now().minus(1, ChronoUnit.MINUTES));
        when(orderRepository.findExpiredPendingOrdersForUpdate(any(Instant.class), any(Pageable.class)))
                .thenReturn(List.of(order));
        when(productRepository.findAllByIdForUpdate(anyList())).thenReturn(List.of(product));

        int expiredCount = checkoutService.expireExpiredOrders();

        assertEquals(1, expiredCount);
        assertEquals("EXPIRED", order.getStatus().name());
        assertEquals(10, product.getStock());
        verify(inventoryMovementRepository).saveAll(anyList());
    }

    @Test
    void expirationJobDoesNothingWhenThereAreNoExpiredOrders() {
        when(orderRepository.findExpiredPendingOrdersForUpdate(any(Instant.class), any(Pageable.class)))
                .thenReturn(List.of());

        assertEquals(0, checkoutService.expireExpiredOrders());

        verify(productRepository, never()).findAllByIdForUpdate(anyList());
        verify(inventoryMovementRepository, never()).saveAll(anyList());
    }

    private OrderEntity createOrderWithItem() {
        OrderEntity order = new OrderEntity(customer, IDEMPOTENCY_KEY, new BigDecimal("159.80"));
        ReflectionTestUtils.setField(order, "id", UUID.randomUUID());
        ReflectionTestUtils.setField(order, "createdAt", Instant.now());
        ReflectionTestUtils.setField(order, "expiresAt", Instant.now().plus(30, ChronoUnit.MINUTES));
        product.reserveStock(2);
        order.addItem(new OrderItemEntity(order, product.getId(), product.getSku(), product.getName(),
                product.getPrice(), 2));
        return order;
    }

    private void arrangeNewCheckout() {
        when(orderRepository.findByCustomerIdAndIdempotencyKey(customer.getId(), IDEMPOTENCY_KEY))
                .thenReturn(Optional.empty());
        when(cartRepository.findByUserIdForUpdate(customer.getId())).thenReturn(Optional.of(cart));
        when(productRepository.findAllByIdForUpdate(anyList())).thenReturn(List.of(product));
    }
}
