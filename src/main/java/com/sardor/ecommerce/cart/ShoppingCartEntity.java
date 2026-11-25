package com.sardor.ecommerce.cart;

import com.sardor.ecommerce.product.ProductEntity;
import com.sardor.ecommerce.user.UserEntity;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OneToOne;
import jakarta.persistence.OrderBy;
import jakarta.persistence.Table;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Entity
@Table(name = "shopping_carts")
public class ShoppingCartEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false, unique = true)
    private UserEntity user;

    @OneToMany(mappedBy = "cart", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("id ASC")
    private List<CartItemEntity> items = new ArrayList<>();

    protected ShoppingCartEntity() {
    }

    public ShoppingCartEntity(UserEntity user) {
        this.user = user;
    }

    public int quantityOf(UUID productId) {
        return items.stream()
                .filter(item -> item.getProduct().getId().equals(productId))
                .mapToInt(CartItemEntity::getQuantity)
                .findFirst()
                .orElse(0);
    }

    public void addProduct(ProductEntity product, int quantity) {
        items.stream()
                .filter(item -> item.getProduct().getId().equals(product.getId()))
                .findFirst()
                .ifPresentOrElse(
                        item -> item.changeQuantity(item.getQuantity() + quantity),
                        () -> items.add(new CartItemEntity(this, product, quantity))
                );
    }

    public void setProductQuantity(UUID productId, int quantity) {
        items.stream()
                .filter(item -> item.getProduct().getId().equals(productId))
                .findFirst()
                .orElseThrow()
                .changeQuantity(quantity);
    }

    public boolean removeProduct(UUID productId) {
        return items.removeIf(item -> item.getProduct().getId().equals(productId));
    }

    public UUID getId() { return id; }
    public UserEntity getUser() { return user; }
    public List<CartItemEntity> getItems() { return items; }
}
