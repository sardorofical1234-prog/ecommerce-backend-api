package com.sardor.ecommerce.cart;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;
import java.util.UUID;

public interface ShoppingCartRepository extends JpaRepository<ShoppingCartEntity, UUID> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select cart from ShoppingCartEntity cart where cart.user.id = :userId")
    Optional<ShoppingCartEntity> findByUserIdForUpdate(@Param("userId") UUID userId);
}
