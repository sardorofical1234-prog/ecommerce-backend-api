package com.sardor.ecommerce.cart;

import com.sardor.ecommerce.common.ConflictException;
import com.sardor.ecommerce.common.NotFoundException;
import com.sardor.ecommerce.common.UnauthorizedException;
import com.sardor.ecommerce.product.ProductEntity;
import com.sardor.ecommerce.product.ProductRepository;
import com.sardor.ecommerce.user.UserEntity;
import com.sardor.ecommerce.user.UserRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;

@Service
public class CartService {
    private final ShoppingCartRepository cartRepository;
    private final UserRepository userRepository;
    private final ProductRepository productRepository;

    public CartService(
            ShoppingCartRepository cartRepository,
            UserRepository userRepository,
            ProductRepository productRepository
    ) {
        this.cartRepository = cartRepository;
        this.userRepository = userRepository;
        this.productRepository = productRepository;
    }

    @Transactional
    public CartResponse getCart(String customerEmail) {
        return toResponse(getOrCreateLockedCart(customerEmail));
    }

    @Transactional
    public CartResponse addItem(String customerEmail, AddCartItemRequest request) {
        ShoppingCartEntity cart = getOrCreateLockedCart(customerEmail);
        ProductEntity product = findActiveProduct(request.productId());
        int newQuantity = cart.quantityOf(product.getId()) + request.quantity();
        ensureAvailableStock(product, newQuantity);
        cart.addProduct(product, request.quantity());
        return toResponse(cart);
    }

    @Transactional
    public CartResponse updateItem(String customerEmail, UUID productId, UpdateCartItemRequest request) {
        ShoppingCartEntity cart = getOrCreateLockedCart(customerEmail);
        if (cart.quantityOf(productId) == 0) {
            throw new NotFoundException("Product is not in the current cart");
        }

        ProductEntity product = findActiveProduct(productId);
        ensureAvailableStock(product, request.quantity());
        cart.setProductQuantity(productId, request.quantity());
        return toResponse(cart);
    }

    @Transactional
    public void removeItem(String customerEmail, UUID productId) {
        ShoppingCartEntity cart = getOrCreateLockedCart(customerEmail);
        if (!cart.removeProduct(productId)) {
            throw new NotFoundException("Product is not in the current cart");
        }
    }

    private ShoppingCartEntity getOrCreateLockedCart(String customerEmail) {
        UserEntity customer = userRepository.findByEmailForUpdate(customerEmail)
                .orElseThrow(() -> new UnauthorizedException("Authenticated customer account was not found"));

        return cartRepository.findByUserIdForUpdate(customer.getId())
                .orElseGet(() -> cartRepository.save(new ShoppingCartEntity(customer)));
    }

    private ProductEntity findActiveProduct(UUID productId) {
        return productRepository.findByIdAndActiveTrue(productId)
                .orElseThrow(() -> new NotFoundException("Active product not found"));
    }

    private void ensureAvailableStock(ProductEntity product, int requestedQuantity) {
        if (requestedQuantity > product.getStock()) {
            throw new ConflictException("Only " + product.getStock() + " unit(s) are currently available");
        }
    }

    private CartResponse toResponse(ShoppingCartEntity cart) {
        List<CartItemResponse> items = cart.getItems().stream()
                .sorted(Comparator.comparing(item -> item.getProduct().getName(), String.CASE_INSENSITIVE_ORDER))
                .map(item -> {
                    ProductEntity product = item.getProduct();
                    BigDecimal lineTotal = product.getPrice().multiply(BigDecimal.valueOf(item.getQuantity()));
                    boolean available = product.isActive() && product.getStock() >= item.getQuantity();
                    return new CartItemResponse(
                            product.getId().toString(),
                            product.getSku(),
                            product.getName(),
                            product.getPrice(),
                            item.getQuantity(),
                            lineTotal,
                            product.getStock(),
                            available
                    );
                })
                .toList();

        int totalItems = items.stream().mapToInt(CartItemResponse::quantity).sum();
        BigDecimal estimatedTotal = items.stream()
                .map(CartItemResponse::lineTotal)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        return new CartResponse(cart.getId().toString(), items, totalItems, estimatedTotal);
    }
}
