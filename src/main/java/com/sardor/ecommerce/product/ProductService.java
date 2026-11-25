package com.sardor.ecommerce.product;

import com.sardor.ecommerce.common.ConflictException;
import com.sardor.ecommerce.common.NotFoundException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Locale;
import java.util.UUID;

@Service
public class ProductService {
    private static final int MAX_PAGE_SIZE = 100;

    private final ProductRepository productRepository;

    public ProductService(ProductRepository productRepository) {
        this.productRepository = productRepository;
    }

    @Transactional(readOnly = true)
    public ProductPageResponse search(String query, int page, int size) {
        if (page < 0) {
            throw new IllegalArgumentException("page must be zero or greater");
        }
        if (size < 1 || size > MAX_PAGE_SIZE) {
            throw new IllegalArgumentException("size must be between 1 and " + MAX_PAGE_SIZE);
        }

        String normalizedQuery = query == null || query.isBlank() ? null : query.trim();
        Page<ProductEntity> result = productRepository.searchActive(
                normalizedQuery,
                PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "createdAt"))
        );
        return new ProductPageResponse(
                result.getContent().stream().map(ProductResponse::from).toList(),
                result.getNumber(), result.getSize(), result.getTotalElements(),
                result.getTotalPages(), result.isLast()
        );
    }

    @Transactional(readOnly = true)
    public ProductResponse getById(UUID id) {
        return productRepository.findByIdAndActiveTrue(id)
                .map(ProductResponse::from)
                .orElseThrow(() -> new NotFoundException("Product not found"));
    }

    @Transactional
    public ProductResponse create(CreateProductRequest request) {
        String sku = request.sku().trim().toUpperCase(Locale.ROOT);
        if (productRepository.existsBySku(sku)) {
            throw new ConflictException("A product with this SKU already exists");
        }
        ProductEntity product = new ProductEntity(sku, request.name(), request.description(),
                request.category(), request.price(), request.stock());
        return ProductResponse.from(productRepository.save(product));
    }

    @Transactional
    public ProductResponse update(UUID id, UpdateProductRequest request) {
        ProductEntity product = productRepository.findByIdAndActiveTrue(id)
                .orElseThrow(() -> new NotFoundException("Product not found"));
        product.update(request.name(), request.description(), request.category(), request.price(), request.stock());
        return ProductResponse.from(product);
    }

    @Transactional
    public void deactivate(UUID id) {
        ProductEntity product = productRepository.findByIdAndActiveTrue(id)
                .orElseThrow(() -> new NotFoundException("Product not found"));
        product.deactivate();
    }
}
