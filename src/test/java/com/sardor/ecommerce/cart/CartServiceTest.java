package com.sardor.ecommerce.cart;

import com.sardor.ecommerce.common.ConflictException;
import com.sardor.ecommerce.common.NotFoundException;
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
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CartServiceTest {
    private static final String CUSTOMER_EMAIL = "buyer@example.com";

    @Mock ShoppingCartRepository cartRepository;
    @Mock UserRepository userRepository;
    @Mock ProductRepository productRepository;
    @InjectMocks CartService cartService;

    private UserEntity customer;
    private ProductEntity product;

    @BeforeEach
    void setUp() {
        customer = new UserEntity(CUSTOMER_EMAIL, "hash", "Test Buyer", UserRole.CUSTOMER);
        ReflectionTestUtils.setField(customer, "id", UUID.randomUUID());

        product = new ProductEntity("KB-100", "Mechanical Keyboard", "Compact keyboard",
                "Accessories", new BigDecimal("79.90"), 10);
        ReflectionTestUtils.setField(product, "id", UUID.randomUUID());
    }

    @Test
    void getCartCreatesAnEmptyCartForAuthenticatedCustomer() {
        arrangeNewCart();

        CartResponse response = cartService.getCart(CUSTOMER_EMAIL);

        assertEquals(0, response.totalItems());
        assertEquals(BigDecimal.ZERO, response.estimatedTotal());
        verify(userRepository).findByEmailForUpdate(CUSTOMER_EMAIL);
    }

    @Test
    void addItemCreatesCartLineAndUsesDatabaseProductPrice() {
        arrangeNewCart();
        when(productRepository.findByIdAndActiveTrue(product.getId())).thenReturn(Optional.of(product));

        CartResponse response = cartService.addItem(CUSTOMER_EMAIL,
                new AddCartItemRequest(product.getId(), 2));

        assertEquals(2, response.totalItems());
        assertEquals(new BigDecimal("159.80"), response.estimatedTotal());
        assertEquals(new BigDecimal("79.90"), response.items().getFirst().unitPrice());
        assertEquals(2, response.items().getFirst().quantity());
    }

    @Test
    void addItemIncreasesQuantityWhenProductAlreadyExists() {
        ShoppingCartEntity cart = arrangeExistingCart();
        cart.addProduct(product, 2);
        when(productRepository.findByIdAndActiveTrue(product.getId())).thenReturn(Optional.of(product));

        CartResponse response = cartService.addItem(CUSTOMER_EMAIL,
                new AddCartItemRequest(product.getId(), 3));

        assertEquals(5, response.totalItems());
        assertEquals(5, response.items().getFirst().quantity());
        assertEquals(new BigDecimal("399.50"), response.estimatedTotal());
    }

    @Test
    void addItemRejectsQuantityAboveCurrentStock() {
        arrangeNewCart();
        when(productRepository.findByIdAndActiveTrue(product.getId())).thenReturn(Optional.of(product));

        assertThrows(ConflictException.class, () -> cartService.addItem(CUSTOMER_EMAIL,
                new AddCartItemRequest(product.getId(), 11)));
    }

    @Test
    void updateItemReplacesQuantityAndRecalculatesTotal() {
        ShoppingCartEntity cart = arrangeExistingCart();
        cart.addProduct(product, 2);
        when(productRepository.findByIdAndActiveTrue(product.getId())).thenReturn(Optional.of(product));

        CartResponse response = cartService.updateItem(CUSTOMER_EMAIL, product.getId(),
                new UpdateCartItemRequest(4));

        assertEquals(4, response.totalItems());
        assertEquals(new BigDecimal("319.60"), response.estimatedTotal());
    }

    @Test
    void removeItemDeletesOnlyTheRequestedLine() {
        ShoppingCartEntity cart = arrangeExistingCart();
        cart.addProduct(product, 2);

        cartService.removeItem(CUSTOMER_EMAIL, product.getId());

        assertEquals(0, cart.getItems().size());
    }

    @Test
    void updateItemReturnsNotFoundWhenProductIsNotInCart() {
        arrangeNewCart();

        assertThrows(NotFoundException.class, () -> cartService.updateItem(CUSTOMER_EMAIL,
                product.getId(), new UpdateCartItemRequest(1)));
    }

    private void arrangeNewCart() {
        when(userRepository.findByEmailForUpdate(CUSTOMER_EMAIL)).thenReturn(Optional.of(customer));
        when(cartRepository.findByUserIdForUpdate(customer.getId())).thenReturn(Optional.empty());
        when(cartRepository.save(any(ShoppingCartEntity.class))).thenAnswer(invocation -> {
            ShoppingCartEntity cart = invocation.getArgument(0);
            ReflectionTestUtils.setField(cart, "id", UUID.randomUUID());
            return cart;
        });
    }

    private ShoppingCartEntity arrangeExistingCart() {
        when(userRepository.findByEmailForUpdate(CUSTOMER_EMAIL)).thenReturn(Optional.of(customer));
        ShoppingCartEntity cart = new ShoppingCartEntity(customer);
        ReflectionTestUtils.setField(cart, "id", UUID.randomUUID());
        when(cartRepository.findByUserIdForUpdate(customer.getId())).thenReturn(Optional.of(cart));
        return cart;
    }
}
