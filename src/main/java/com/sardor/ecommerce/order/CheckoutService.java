package com.sardor.ecommerce.order;

import com.sardor.ecommerce.cart.CartItemEntity;
import com.sardor.ecommerce.cart.ShoppingCartEntity;
import com.sardor.ecommerce.cart.ShoppingCartRepository;
import com.sardor.ecommerce.common.ConflictException;
import com.sardor.ecommerce.common.NotFoundException;
import com.sardor.ecommerce.common.UnauthorizedException;
import com.sardor.ecommerce.inventory.InventoryMovementEntity;
import com.sardor.ecommerce.inventory.InventoryMovementRepository;
import com.sardor.ecommerce.inventory.InventoryMovementType;
import com.sardor.ecommerce.product.ProductEntity;
import com.sardor.ecommerce.product.ProductRepository;
import com.sardor.ecommerce.user.UserEntity;
import com.sardor.ecommerce.user.UserRepository;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
public class CheckoutService {
    private final UserRepository userRepository;
    private final ShoppingCartRepository cartRepository;
    private final ProductRepository productRepository;
    private final OrderRepository orderRepository;
    private final InventoryMovementRepository inventoryMovementRepository;

    public CheckoutService(UserRepository userRepository, ShoppingCartRepository cartRepository,
                           ProductRepository productRepository, OrderRepository orderRepository,
                           InventoryMovementRepository inventoryMovementRepository) {
        this.userRepository = userRepository;
        this.cartRepository = cartRepository;
        this.productRepository = productRepository;
        this.orderRepository = orderRepository;
        this.inventoryMovementRepository = inventoryMovementRepository;
    }

    @Transactional
    public OrderResponse checkout(String customerEmail, String idempotencyKey) {
        UserEntity customer = userRepository.findByEmailForUpdate(customerEmail)
                .orElseThrow(() -> new UnauthorizedException("Authenticated customer account was not found"));

        var previousOrder = orderRepository.findByCustomerIdAndIdempotencyKey(customer.getId(), idempotencyKey);
        if (previousOrder.isPresent()) {
            return OrderResponse.from(previousOrder.get());
        }

        ShoppingCartEntity cart = cartRepository.findByUserIdForUpdate(customer.getId())
                .orElseThrow(() -> new ConflictException("Your shopping cart is empty"));
        if (cart.getItems().isEmpty()) {
            throw new ConflictException("Your shopping cart is empty");
        }

        List<UUID> productIds = cart.getItems().stream()
                .map(item -> item.getProduct().getId())
                .distinct()
                .sorted()
                .toList();
        Map<UUID, ProductEntity> productsById = indexProducts(productRepository.findAllByIdForUpdate(productIds));

        List<CheckoutLine> lines = new ArrayList<>();
        BigDecimal total = BigDecimal.ZERO;
        for (CartItemEntity cartItem : cart.getItems()) {
            ProductEntity product = productsById.get(cartItem.getProduct().getId());
            if (product == null || !product.isActive()) {
                throw new ConflictException("A product in your cart is no longer available");
            }
            int quantity = cartItem.getQuantity();
            if (quantity > product.getStock()) {
                throw new ConflictException("Insufficient stock for product " + product.getSku()
                        + "; available quantity: " + product.getStock());
            }
            total = total.add(product.getPrice().multiply(BigDecimal.valueOf(quantity)));
            lines.add(new CheckoutLine(product, quantity));
        }

        OrderEntity order = new OrderEntity(customer, idempotencyKey, total);
        List<InventoryMovementEntity> movements = new ArrayList<>();
        for (CheckoutLine line : lines) {
            ProductEntity product = line.product();
            product.reserveStock(line.quantity());
            order.addItem(new OrderItemEntity(order, product.getId(), product.getSku(),
                    product.getName(), product.getPrice(), line.quantity()));
            movements.add(new InventoryMovementEntity(product, order, InventoryMovementType.RESERVED,
                    line.quantity(), product.getStock()));
        }

        orderRepository.saveAndFlush(order);
        inventoryMovementRepository.saveAll(movements);
        cart.getItems().clear();
        return OrderResponse.from(order);
    }

    @Transactional
    public OrderResponse cancel(String customerEmail, UUID orderId) {
        UserEntity customer = userRepository.findByEmailForUpdate(customerEmail)
                .orElseThrow(() -> new UnauthorizedException("Authenticated customer account was not found"));
        OrderEntity order = orderRepository.findByIdAndCustomerIdForUpdate(orderId, customer.getId())
                .orElseThrow(() -> new NotFoundException("Order not found"));

        if (order.getStatus() == OrderStatus.CANCELLED || order.getStatus() == OrderStatus.EXPIRED) {
            return OrderResponse.from(order);
        }
        if (order.getStatus() != OrderStatus.PENDING_PAYMENT) {
            throw new ConflictException("This order can no longer be cancelled");
        }

        Map<UUID, ProductEntity> productsById = lockOrderProducts(List.of(order));
        List<InventoryMovementEntity> movements = restoreOrderInventory(order, productsById);
        order.cancel();
        inventoryMovementRepository.saveAll(movements);
        return OrderResponse.from(order);
    }

    @Transactional
    public int expireExpiredOrders() {
        List<OrderEntity> expiredOrders = orderRepository.findExpiredPendingOrdersForUpdate(
                Instant.now(), PageRequest.of(0, 50));
        if (expiredOrders.isEmpty()) {
            return 0;
        }

        Map<UUID, ProductEntity> productsById = lockOrderProducts(expiredOrders);
        List<InventoryMovementEntity> movements = new ArrayList<>();
        for (OrderEntity order : expiredOrders) {
            movements.addAll(restoreOrderInventory(order, productsById));
            order.expire();
        }
        inventoryMovementRepository.saveAll(movements);
        return expiredOrders.size();
    }

    private Map<UUID, ProductEntity> lockOrderProducts(List<OrderEntity> orders) {
        List<UUID> productIds = orders.stream()
                .flatMap(order -> order.getItems().stream())
                .map(OrderItemEntity::getProductId)
                .distinct()
                .sorted()
                .toList();
        return indexProducts(productRepository.findAllByIdForUpdate(productIds));
    }

    private List<InventoryMovementEntity> restoreOrderInventory(
            OrderEntity order,
            Map<UUID, ProductEntity> productsById
    ) {
        List<InventoryMovementEntity> movements = new ArrayList<>();
        for (OrderItemEntity item : order.getItems()) {
            ProductEntity product = productsById.get(item.getProductId());
            if (product == null) {
                throw new IllegalStateException("Cannot restore inventory: product record is missing");
            }
            product.releaseStock(item.getQuantity());
            movements.add(new InventoryMovementEntity(product, order, InventoryMovementType.RELEASED,
                    item.getQuantity(), product.getStock()));
        }
        return movements;
    }

    private Map<UUID, ProductEntity> indexProducts(List<ProductEntity> products) {
        Map<UUID, ProductEntity> productsById = new HashMap<>();
        products.forEach(product -> productsById.put(product.getId(), product));
        return productsById;
    }

    private record CheckoutLine(ProductEntity product, int quantity) {
    }
}
