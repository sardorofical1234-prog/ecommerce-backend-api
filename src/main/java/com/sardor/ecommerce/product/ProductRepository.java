package com.sardor.ecommerce.product;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;
import java.util.UUID;

public interface ProductRepository extends JpaRepository<ProductEntity, UUID> {
    boolean existsBySku(String sku);

    @Query("""
            select p from ProductEntity p
            where p.active = true
              and (:query is null
                   or lower(p.name) like lower(concat('%', :query, '%'))
                   or lower(p.sku) like lower(concat('%', :query, '%'))
                   or lower(p.category) like lower(concat('%', :query, '%')))
            """)
    Page<ProductEntity> searchActive(@Param("query") String query, Pageable pageable);

    Optional<ProductEntity> findByIdAndActiveTrue(UUID id);
}
