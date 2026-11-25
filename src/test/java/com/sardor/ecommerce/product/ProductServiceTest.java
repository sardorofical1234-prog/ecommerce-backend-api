package com.sardor.ecommerce.product;

import com.sardor.ecommerce.common.ConflictException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.UUID;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ProductServiceTest {
    @Mock ProductRepository productRepository;
    @InjectMocks ProductService productService;

    @Test
    void createNormalizesSkuAndPersistsProduct() {
        when(productRepository.existsBySku("SKU-100")).thenReturn(false);
        when(productRepository.save(any(ProductEntity.class))).thenAnswer(invocation -> {
            ProductEntity saved = invocation.getArgument(0);
            ReflectionTestUtils.setField(saved, "id", UUID.randomUUID());
            return saved;
        });
        CreateProductRequest request = new CreateProductRequest(
                "sku-100", "Mechanical Keyboard", "Compact keyboard", "Accessories", new BigDecimal("79.90"), 12);

        ProductResponse response = productService.create(request);

        assertEquals("SKU-100", response.sku());
        assertEquals(new BigDecimal("79.90"), response.price());
        verify(productRepository).save(any(ProductEntity.class));
    }

    @Test
    void createRejectsDuplicateSku() {
        when(productRepository.existsBySku("SKU-100")).thenReturn(true);
        CreateProductRequest request = new CreateProductRequest(
                "sku-100", "Mechanical Keyboard", null, "Accessories", new BigDecimal("79.90"), 12);

        assertThrows(ConflictException.class, () -> productService.create(request));
        verify(productRepository, never()).save(any());
    }
}
